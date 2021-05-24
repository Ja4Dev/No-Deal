package com.juego.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.juego.core.Core;
import com.juego.core.GameWorld;
import com.juego.utils.Global;



public class ClientThread extends Thread {
	
	private DatagramSocket conexion;
	private InetAddress ipServer;
	private int puerto = 25559;
	private boolean fin = false;
	private GameWorld app;
	JFrame frame;
	
	public ClientThread(GameWorld app) {
		this.app = app;
		frame = new JFrame(); 
		try {
			ipServer = InetAddress.getByName(JOptionPane.showInputDialog(frame,"Ingrese la IP"));
			conexion = new DatagramSocket();
			System.out.println(conexion.getInetAddress());
		} catch (SocketException | UnknownHostException e) {
			e.printStackTrace();
		}
		sendMessage("Conexion");
	}
	
	public void sendMessage(String msg) {
		byte[] data = msg.getBytes();
		DatagramPacket dp = new DatagramPacket(data, data.length,ipServer,puerto);
		try {
			conexion.send(dp);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		do {
			byte[] data = new byte[1024];
			DatagramPacket dp = new DatagramPacket(data, data.length);
			try {
				conexion.receive(dp);
			} catch (IOException e) {
				e.printStackTrace();
			}
//			processMessage(dp);
		}while(!fin);
	}

//	private void processMessage(DatagramPacket dp) {
//		String msg = (new String(dp.getData())).trim();
//		
//		String[] mensajeParametrizado = msg.split("-");
//		
//		if(mensajeParametrizado.length<2) {
//			if(msg.equals("Empieza")) {
//				Global.start = true;
//			} else if(msg.equals("TerminaJuego")) {
//				Global.endGame = true;
//			}
//		} else {
//			if(mensajeParametrizado[0].equals("OK")) {
//				ipServer = dp.getAddress();
//				app.nroJugador = Integer.parseInt(mensajeParametrizado[1]);
//			} else if(mensajeParametrizado[0].equals("Actualizar")){
//				if(mensajeParametrizado[1].equals("P1")) {
//					try {
//						float posX = Float.parseFloat(mensajeParametrizado[2]);
//						app.p1.setPosX(posX);
//						float posY = Float.parseFloat(mensajeParametrizado[3]);
//						app.p1.setPosY(posY);
//					}catch(Exception e) {}
//				} if(mensajeParametrizado[1].equals("P2")) {
//					try {
//						float posX = Float.parseFloat(mensajeParametrizado[2]);
//						app.p2.setPosX(posX);
//						float posY = Float.parseFloat(mensajeParametrizado[3]);
//						app.p2.setPosY(posY);
//					}catch(Exception e) {}
//				} else if(mensajeParametrizado[1].equals("Pelota")) { 
//					try {
//						float posX = Float.parseFloat(mensajeParametrizado[2]);
//						float posY = Float.parseFloat(mensajeParametrizado[3]);
//						app.ball.setPosition(posX, posY);					  					
//					}catch(Exception e) {}
//				}
//			} else if(mensajeParametrizado[0].equals("ActualizarPuntaje")){
//				if(mensajeParametrizado[1].equals("P1")) {
//					Global.goals1 = Integer.parseInt(mensajeParametrizado[2]);
//				} else {
//					Global.goals2 = Integer.parseInt(mensajeParametrizado[2]);
//				}
//			}
//		}
//	}
	
}
