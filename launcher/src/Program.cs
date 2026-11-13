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
            for (int i = 0; i < args.Length; i++)
            {
                string arg = args[i];
                string option = arg.ToLowerInvariant();
                if (mode == GameMode.Server)
                    serverArgs.Add(arg);
                else if (option == "--lang" && i + 1 < args.Length && L.Set(args[i + 1]))
                    i++;
                else if (option == "--client" || option == "--cliente" || option == "--jugar")
                    mode = GameMode.Client;
                else if (option == "--server" || option == "--servidor")
                    mode = GameMode.Server;
                else
                {
                    bool help = option == "--help" || option == "-h" || option == "/?";
                    MessageDialog.Error(null, help ? L.T("Opciones de NoDeal.exe", "NoDeal.exe options")
                            : L.T("Opción desconocida: ", "Unknown option: ") + arg,
                            L.T("NoDeal.exe                         abre el launcher\n"
                            + "NoDeal.exe --client                abre el juego directamente\n"
                            + "NoDeal.exe --server [opciones]     abre el servidor dedicado\n"
                            + "NoDeal.exe --lang es|en            elige el idioma\n\n"
                            + "Las opciones del servidor son las de NoDeal-server.jar, por ejemplo:\n"
                            + "--port 25559 --max-players 4 --bots 4 --nogui\n"
                            + "(NoDeal.exe --server --help las lista todas).",
                            "NoDeal.exe                         opens the launcher\n"
                            + "NoDeal.exe --client                opens the game directly\n"
                            + "NoDeal.exe --server [options]      opens the dedicated server\n"
                            + "NoDeal.exe --lang es|en            chooses the language\n\n"
                            + "The server options are NoDeal-server.jar's, for example:\n"
                            + "--port 25559 --max-players 4 --bots 4 --nogui\n"
                            + "(NoDeal.exe --server --help lists them all)."));
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
