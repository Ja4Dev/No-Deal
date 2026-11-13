using System;
using System.Collections.Generic;
using System.Windows.Forms;

namespace NoDeal.Launcher
{
    /// <summary>
    /// NoDeal.exe: the No Deal launcher for Windows.
    ///
    ///   NoDeal.exe                          opens the launcher window
    ///   NoDeal.exe --client                 starts the game right away
    ///   NoDeal.exe --server [options]       starts the dedicated server; the options go to the
    ///                                       server (--port 25559 --bots 4 --nogui ...)
    ///
    /// It needs only the .NET Framework that comes with Windows 10 and 11.
    /// </summary>
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            GameMode? mode = null;
            var serverArgs = new List<string>();
            foreach (string arg in args)
            {
                string option = arg.ToLowerInvariant();
                if (mode == GameMode.Server)
                    serverArgs.Add(arg);
                else if (option == "--client" || option == "--cliente" || option == "--jugar")
                    mode = GameMode.Client;
                else if (option == "--server" || option == "--servidor")
                    mode = GameMode.Server;
                else
                {
                    bool help = option == "--help" || option == "-h" || option == "/?";
                    MessageDialog.Error(null, help ? "Opciones de NoDeal.exe" : "Opción desconocida: " + arg,
                            "NoDeal.exe                         abre el launcher\n"
                            + "NoDeal.exe --client                abre el juego directamente\n"
                            + "NoDeal.exe --server [opciones]     abre el servidor dedicado\n\n"
                            + "Las opciones del servidor son las de NoDeal-server.jar, por ejemplo:\n"
                            + "--port 25559 --max-players 4 --bots 4 --nogui\n"
                            + "(NoDeal.exe --server --help las lista todas).");
                    return help ? 0 : 2;
                }
            }

            if (mode == null)
            {
                Application.Run(new LauncherForm());
                return 0;
            }
            return LaunchFlow.RunDirect(mode.Value, serverArgs);
        }
    }
}
