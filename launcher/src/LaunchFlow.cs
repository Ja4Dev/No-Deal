using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows.Forms;

namespace NoDeal.Launcher
{
    /// <summary>
    /// The steps between "Jugar" and a running game, shared by the window and
    /// by "NoDeal.exe --client / --server": check the files, find Java (or say
    /// what to install), start it and explain the crash if it does not work.
    /// </summary>
    static class LaunchFlow
    {
        public static string JavaDownloadUrl
        {
            get
            {
                return "https://adoptium.net/temurin/releases/?os=windows&arch="
                        + (Environment.Is64BitOperatingSystem ? "x64" : "x86")
                        + "&package=jre&version=" + JavaFinder.RequiredMajor;
            }
        }

        /// <summary>True if the jar is where it should be; otherwise tells the player how to fix it.</summary>
        public static bool CheckFiles(IWin32Window owner, GameMode mode)
        {
            string jar = Paths.Jar(mode);
            if (File.Exists(jar))
                return true;
            if (Paths.RunningFromZip())
                MessageDialog.Error(owner, "Primero descomprimí el zip",
                        "Abriste NoDeal.exe desde adentro del .zip, y así Windows no le deja ver el resto del juego.\n\n"
                        + "Cerrá esto, hacé clic derecho en el zip → \"Extraer todo...\" y abrí NoDeal.exe desde la "
                        + "carpeta que se crea.");
            else
                MessageDialog.Error(owner, "Faltan archivos del juego",
                        "No encuentro " + jar + ".\n\nNoDeal.exe tiene que estar junto a la carpeta \"game\" que viene "
                        + "en el zip. Volvé a descomprimir el zip completo.");
            return false;
        }

        /// <summary>
        /// The Java to run the game with. When there is none, explains what to
        /// install and searches again when asked, until one shows up or the
        /// player gives up (null).
        /// </summary>
        public static JavaInstall EnsureJava(IWin32Window owner, ref JavaSearch search)
        {
            while (search.Best == null)
            {
                var dialog = new MessageDialog("No Deal", "Necesitás Java " + JavaFinder.RequiredMajor + " o más nuevo",
                        DescribeMissingJava(search), null);
                dialog.AddButton("Cerrar", false, null)
                      .AddButton("Volver a buscar", false, null)
                      .AddButton("Descargar Java", true, () =>
                      {
                          OpenUrl(JavaDownloadUrl);
                          return false;
                      });
                if (dialog.ShowFor(owner) != "Volver a buscar")
                    return null;
                Cursor.Current = Cursors.WaitCursor;
                search = JavaFinder.Search();
                Cursor.Current = Cursors.Default;
            }
            return search.Best;
        }

        static string DescribeMissingJava(JavaSearch search)
        {
            var text = new StringBuilder("No Deal está hecho en Java y no encontré una versión que lo pueda correr en esta PC.");
            var listed = new HashSet<string>();
            foreach (JavaInstall java in search.Found)
            {
                string line = java.Describe() + ": " + java.Problem();
                if (listed.Count == 6 || !listed.Add(line))
                    continue;
                if (listed.Count == 1)
                    text.Append("\n\nEncontré estos, pero no sirven:");
                text.Append("\n   •  ").Append(line);
            }
            if (search.Found.Count == 0)
                text.Append(" No hay ningún Java instalado.");
            text.Append("\n\nCómo arreglarlo:")
                .Append("\n   1.  Tocá \"Descargar Java\": se abre la página de Eclipse Temurin (gratis).")
                .Append("\n   2.  Bajá el instalador .msi de Java ").Append(JavaFinder.RequiredMajor)
                .Append(" e instalalo con las opciones que trae.")
                .Append("\n   3.  Volvé acá y tocá \"Volver a buscar\".");
            return text.ToString();
        }

        /// <summary>Starts the game, telling the player if Java itself could not be started (null then).</summary>
        public static GameRun Start(IWin32Window owner, JavaInstall java, GameMode mode, IList<string> args, bool console)
        {
            try
            {
                return GameRun.Start(java, mode, args, console);
            }
            catch (Exception e)
            {
                MessageDialog.Error(owner, "No se pudo iniciar Java",
                        e.Message + "\n\n" + java.JavaExe + "\n\nSi el antivirus lo bloqueó, agregá una excepción "
                        + "para la carpeta de No Deal.");
                return null;
            }
        }

        /// <summary>Explains why the game closed, with its log. True if the player wants to try again.</summary>
        public static bool ShowCrash(IWin32Window owner, GameRun run, int exitCode)
        {
            string log = run.ReadLog();
            Diagnosis diagnosis = Diagnosis.For(run, exitCode, log);
            var dialog = new MessageDialog("No Deal", diagnosis.Heading, diagnosis.Advice, Diagnosis.Tail(log, 40));
            if (diagnosis.CanRetry)
                dialog.AddButton("Reintentar", true, null);
            dialog.AddButton("Cerrar", !diagnosis.CanRetry, null)
                  .AddButton("Copiar", false, () =>
                  {
                      try { Clipboard.SetText(diagnosis.Heading + "\r\n\r\n" + dialog.DetailText); }
                      catch (Exception) { }
                      return false;
                  })
                  .AddButton("Abrir log", false, () =>
                  {
                      OpenFile(run.LogPath);
                      return false;
                  });
            return dialog.ShowFor(owner) == "Reintentar";
        }

        /// <summary>"NoDeal.exe --client" or "--server [options]": no launcher window, only dialogs if something fails.</summary>
        public static int RunDirect(GameMode mode, IList<string> args)
        {
            // The server's terminal mode needs a console to type commands in.
            bool console = mode == GameMode.Server
                    && (args.Contains("--nogui") || args.Contains("--help") || args.Contains("-h"));
            if (!CheckFiles(null, mode))
                return 1;
            JavaSearch search = JavaFinder.Search();
            while (true)
            {
                JavaInstall java = EnsureJava(null, ref search);
                if (java == null)
                    return 1;
                GameRun run = Start(null, java, mode, args, console);
                if (run == null)
                    return 1;
                int code = run.WaitForExit();
                if (code == 0 || run.LogPath == null || !ShowCrash(null, run, code))
                    return code;
            }
        }

        public static void OpenUrl(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); }
            catch (Exception) { MessageDialog.Error(null, "No se pudo abrir el navegador", "Entrá a mano a:\n\n" + url); }
        }

        public static void OpenFile(string path)
        {
            try { Process.Start(new ProcessStartInfo(path) { UseShellExecute = true }); }
            catch (Exception) { Process.Start("notepad.exe", "\"" + path + "\""); }
        }

        public static void OpenLogFolder()
        {
            Directory.CreateDirectory(Paths.LogDir);
            Process.Start("explorer.exe", "\"" + Paths.LogDir + "\"");
        }
    }
}
