package com.juego.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidEventListener;
import com.juego.net.Bluetooth;
import com.juego.net.LinkTransport;

/**
 * Bluetooth multiplayer on Android with Bluetooth Classic RFCOMM sockets: the
 * host listens on the game's service UUID and every phone that connects
 * becomes a link of the match (see {@link LinkTransport}).
 * <p>
 * The sockets are "insecure": the phones don't have to be paired first. The
 * joining phone finds the host either among its paired devices or by
 * scanning, which only sees the host while it is visible (the host asks for
 * that when it creates the match).
 * <p>
 * Android asks the player for permissions (which ones depends on the
 * version), to turn Bluetooth on and to make the phone visible; all of that
 * is chained in {@link #prepare}. Results come back to the game thread.
 */
public class AndroidBluetooth implements Bluetooth, AndroidEventListener {
	/** The game's Bluetooth service: the host listens on it, the others connect to it. */
	static final UUID SERVICE = UUID.fromString("5b6c2a1e-8f3d-4c7a-9e21-6d0b4f3a7c19");
	private static final String SERVICE_NAME = "No Deal";
	private static final int REQUEST_PERMISSIONS = 4101, REQUEST_ENABLE = 4102, REQUEST_VISIBLE = 4103;
	/** How long the host stays visible to phones that never paired with it. */
	private static final int VISIBLE_SECONDS = 300;

	private final Activity activity;
	private final BluetoothAdapter adapter;

	// The step of prepare() waiting for the player, if any.
	private Callback<Void> pending;
	private boolean pendingHosting;

	private BroadcastReceiver scanReceiver;

	public AndroidBluetooth(Activity activity) {
		this.activity = activity;
		BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
		adapter = manager == null ? null : manager.getAdapter();
	}

	@Override
	public boolean isSupported() {
		return adapter != null;
	}

	@Override
	public String localName() {
		try {
			String name = adapter.getName();
			if (name != null && !name.isEmpty())
				return name;
		} catch (SecurityException e) {
			// no permission yet
		}
		return "este telefono";
	}

	// ---- Getting ready: permissions, Bluetooth on, visible ----

	@Override
	public void prepare(boolean hosting, Callback<Void> done) {
		activity.runOnUiThread(() -> {
			pending = done;
			pendingHosting = hosting;
			askPermissions();
		});
	}

	/** What Android asks the player for, by version. */
	private static String[] permissions(boolean hosting) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
			return hosting
					? new String[] { Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
							Manifest.permission.BLUETOOTH_ADVERTISE }
					: new String[] { Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN };
		// Up to Android 11, finding nearby devices needs the location permission.
		return hosting ? new String[0]
				: new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
	}

	private void askPermissions() {
		List<String> missing = new ArrayList<>();
		for (String permission : permissions(pendingHosting))
			if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
				missing.add(permission);
		if (missing.isEmpty())
			askToTurnOn();
		else
			activity.requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
	}

	/** Forwarded by the activity. */
	void onPermissionsResult(int requestCode, int[] results) {
		if (requestCode != REQUEST_PERMISSIONS || pending == null)
			return;
		for (int result : results) {
			if (result != PackageManager.PERMISSION_GRANTED) {
				fail(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
						? "Para jugar por Bluetooth hace falta el permiso de dispositivos cercanos."
								+ " Podes darlo en Ajustes > Apps > No Deal > Permisos."
						: "Para buscar telefonos por Bluetooth, Android pide el permiso de ubicacion."
								+ " Podes darlo en Ajustes > Apps > No Deal > Permisos.");
				return;
			}
		}
		askToTurnOn();
	}

	private void askToTurnOn() {
		try {
			if (adapter.isEnabled())
				askToBeVisible();
			else
				activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE);
		} catch (SecurityException e) {
			fail("Android no dejo usar el Bluetooth: " + e.getMessage());
		}
	}

	private void askToBeVisible() {
		try {
			if (!pendingHosting || adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
				succeed();
				return;
			}
			Intent visible = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			visible.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, VISIBLE_SECONDS);
			activity.startActivityForResult(visible, REQUEST_VISIBLE);
		} catch (SecurityException e) {
			fail("Android no dejo usar el Bluetooth: " + e.getMessage());
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (pending == null)
			return;
		if (requestCode == REQUEST_ENABLE) {
			if (resultCode == Activity.RESULT_OK)
				askToBeVisible();
			else
				fail("Para jugar por Bluetooth hay que prenderlo.");
		} else if (requestCode == REQUEST_VISIBLE) {
			// Not visible is fine for phones already paired with this one; the others won't find it.
			succeed();
		}
	}

	private void succeed() {
		Callback<Void> done = pending;
		pending = null;
		Gdx.app.postRunnable(() -> done.ready(null));
	}

	private void fail(String reason) {
		Callback<Void> done = pending;
		pending = null;
		if (done != null)
			Gdx.app.postRunnable(() -> done.failed(reason));
	}

	// ---- Hosting ----

	@Override
	public LinkTransport host() throws IOException {
		final BluetoothServerSocket server;
		try {
			server = adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE);
		} catch (SecurityException e) {
			throw new IOException("sin permiso de Bluetooth");
		}
		final LinkTransport transport = new LinkTransport("Bluetooth") {
			@Override
			public void close() {
				super.close();
				try {
					server.close(); // stops accepting
				} catch (IOException e) {
					// already closed
				}
			}
		};
		Thread accepter = new Thread(() -> {
			while (true) {
				BluetoothSocket socket;
				try {
					socket = server.accept();
				} catch (IOException e) {
					return; // closed: the match ended
				}
				try {
					transport.addLink(deviceName(socket.getRemoteDevice()), socket.getInputStream(),
							socket.getOutputStream(), socket);
				} catch (IOException e) {
					closeQuietly(socket);
				}
			}
		}, "Bluetooth-accept");
		accepter.setDaemon(true);
		accepter.start();
		return transport;
	}

	// ---- Joining ----

	@Override
	public void startScan(DeviceListener listener) {
		activity.runOnUiThread(() -> {
			stopScanNow();
			try {
				for (BluetoothDevice device : adapter.getBondedDevices())
					found(listener, device, true);
				scanReceiver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
							BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
							if (device != null)
								found(listener, device, false);
						} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())
								&& scanReceiver == this) {
							try {
								adapter.startDiscovery(); // keep looking while the list is open
							} catch (SecurityException e) {
								// permission revoked meanwhile
							}
						}
					}
				};
				IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
				filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
				// System broadcasts only: nothing outside Android has to reach this receiver.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
					activity.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
				else
					activity.registerReceiver(scanReceiver, filter);
				adapter.startDiscovery();
			} catch (SecurityException e) {
				// without permission the list only stays empty: prepare() asks for it first
			}
		});
	}

	private void found(DeviceListener listener, BluetoothDevice device, boolean paired) {
		// Leave out what surely isn't a phone (headphones, speakers, watches, keyboards...). Some phones
		// announce odd classes, so anything else is listed.
		int major;
		try {
			major = device.getBluetoothClass() == null ? -1 : device.getBluetoothClass().getMajorDeviceClass();
		} catch (SecurityException e) {
			major = -1; // can't tell: list it
		}
		switch (major) {
		case BluetoothClass.Device.Major.AUDIO_VIDEO:
		case BluetoothClass.Device.Major.WEARABLE:
		case BluetoothClass.Device.Major.PERIPHERAL:
		case BluetoothClass.Device.Major.HEALTH:
		case BluetoothClass.Device.Major.IMAGING:
		case BluetoothClass.Device.Major.TOY:
			return;
		default:
			break;
		}
		final Device found = new Device(deviceName(device), device.getAddress(), paired);
		Gdx.app.postRunnable(() -> listener.found(found));
	}

	@Override
	public void stopScan() {
		activity.runOnUiThread(this::stopScanNow);
	}

	private void stopScanNow() {
		if (scanReceiver != null) {
			try {
				activity.unregisterReceiver(scanReceiver);
			} catch (IllegalArgumentException e) {
				// not registered
			}
			scanReceiver = null;
		}
		try {
			adapter.cancelDiscovery();
		} catch (SecurityException e) {
			// no permission: nothing to cancel either
		}
	}

	@Override
	public void connect(Device device, Callback<Joined> done) {
		Thread connector = new Thread(() -> {
			BluetoothSocket socket = null;
			try {
				adapter.cancelDiscovery(); // scanning slows connections down a lot
				BluetoothDevice remote = adapter.getRemoteDevice(device.address);
				socket = remote.createInsecureRfcommSocketToServiceRecord(SERVICE);
				socket.connect();
				LinkTransport transport = new LinkTransport("Bluetooth");
				Joined joined = new Joined(transport,
						transport.addLink(device.name, socket.getInputStream(), socket.getOutputStream(), socket));
				Gdx.app.postRunnable(() -> done.ready(joined));
			} catch (IOException | SecurityException | IllegalArgumentException e) {
				closeQuietly(socket);
				String reason = "No se pudo conectar con " + device.name + ". Fijate que haya creado la partida"
						+ " con Conexion: Bluetooth y que siga en el juego.";
				Gdx.app.postRunnable(() -> done.failed(reason));
			}
		}, "Bluetooth-connect");
		connector.setDaemon(true);
		connector.start();
	}

	private static String deviceName(BluetoothDevice device) {
		try {
			String name = device.getName();
			if (name != null && !name.isEmpty())
				return name;
		} catch (SecurityException e) {
			// no permission
		}
		return device.getAddress();
	}

	private static void closeQuietly(BluetoothSocket socket) {
		if (socket == null)
			return;
		try {
			socket.close();
		} catch (IOException e) {
			// nothing to do
		}
	}
}
