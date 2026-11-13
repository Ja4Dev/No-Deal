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
        // Buttons whose answer is checked: the same text where they are made and where they are compared.
        static string SearchAgain { get { return L.T("Volver a buscar", "Search again"); } }
        static string Retry { get { return L.T("Reintentar", "Retry"); } }

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
                MessageDialog.Error(owner, L.T("Primero descomprimí el zip", "Extract the zip first"),
                        L.T("Abriste NoDeal.exe desde adentro del .zip, y así Windows no le deja ver el resto del juego.\n\n"
                        + "Cerrá esto, hacé clic derecho en el zip → \"Extraer todo...\" y abrí NoDeal.exe desde la "
                        + "carpeta que se crea.",
                        "You opened NoDeal.exe from inside the .zip, and that way Windows doesn't let it see the rest of the game.\n\n"
                        + "Close this, right-click the zip → \"Extract All...\" and open NoDeal.exe from the folder it creates."));
            else
                MessageDialog.Error(owner, L.T("Faltan archivos del juego", "Game files are missing"),
                        L.T("No encuentro " + jar + ".\n\nNoDeal.exe tiene que estar junto a la carpeta \"game\" que viene "
                        + "en el zip. Volvé a descomprimir el zip completo.",
                        "Can't find " + jar + ".\n\nNoDeal.exe has to be next to the \"game\" folder that comes "
                        + "in the zip. Extract the whole zip again."));
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
                var dialog = new MessageDialog("No Deal", L.T("Necesitás Java " + JavaFinder.RequiredMajor + " o más nuevo",
                        "You need Java " + JavaFinder.RequiredMajor + " or newer"),
                        DescribeMissingJava(search), null);
                dialog.AddButton(L.T("Cerrar", "Close"), false, null)
                      .AddButton(SearchAgain, false, null)
                      .AddButton(L.T("Descargar Java", "Download Java"), true, () =>
                      {
                          OpenUrl(JavaDownloadUrl);
                          return false;
                      });
                if (dialog.ShowFor(owner) != SearchAgain)
                    return null;
                Cursor.Current = Cursors.WaitCursor;
                search = JavaFinder.Search();
                Cursor.Current = Cursors.Default;
            }
            return search.Best;
        }

        static string DescribeMissingJava(JavaSearch search)
        {
            var text = new StringBuilder(L.T("No Deal está hecho en Java y no encontré una versión que lo pueda correr en esta PC.",
                    "No Deal is made in Java, and I couldn't find a version that can run it on this PC."));
            var listed = new HashSet<string>();
            foreach (JavaInstall java in search.Found)
            {
                string line = java.Describe() + ": " + java.Problem();
                if (listed.Count == 6 || !listed.Add(line))
                    continue;
                if (listed.Count == 1)
                    text.Append(L.T("\n\nEncontré estos, pero no sirven:", "\n\nI found these, but they won't do:"));
                text.Append("\n   •  ").Append(line);
            }
            if (search.Found.Count == 0)
                text.Append(L.T(" No hay ningún Java instalado.", " There is no Java installed."));
            text.Append(L.T("\n\nCómo arreglarlo:"
                + "\n   1.  Tocá \"Descargar Java\": se abre la página de Eclipse Temurin (gratis)."
                + "\n   2.  Bajá el instalador .msi de Java " + JavaFinder.RequiredMajor + " e instalalo con las opciones que trae."
                + "\n   3.  Volvé acá y tocá \"Volver a buscar\".",
                "\n\nHow to fix it:"
                + "\n   1.  Press \"Download Java\": it opens the Eclipse Temurin page (free)."
                + "\n   2.  Download the .msi installer of Java " + JavaFinder.RequiredMajor + " and install it with its default options."
                + "\n   3.  Come back here and press \"Search again\"."));
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
                MessageDialog.Error(owner, L.T("No se pudo iniciar Java", "Couldn't start Java"),
                        e.Message + "\n\n" + java.JavaExe + L.T("\n\nSi el antivirus lo bloqueó, agregá una excepción "
                        + "para la carpeta de No Deal.", "\n\nIf the antivirus blocked it, add an exception for the No Deal folder."));
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
                dialog.AddButton(Retry, true, null);
            dialog.AddButton(L.T("Cerrar", "Close"), !diagnosis.CanRetry, null)
                  .AddButton(L.T("Copiar", "Copy"), false, () =>
                  {
                      try { Clipboard.SetText(diagnosis.Heading + "\r\n\r\n" + dialog.DetailText); }
                      catch (Exception) { }
                      return false;
                  })
                  .AddButton(L.T("Abrir log", "Open log"), false, () =>
                  {
                      OpenFile(run.LogPath);
                      return false;
                  });
            return dialog.ShowFor(owner) == Retry;
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
            catch (Exception)
            {
                MessageDialog.Error(null, L.T("No se pudo abrir el navegador", "Couldn't open the browser"),
                        L.T("Entrá a mano a:\n\n", "Go there by hand:\n\n") + url);
            }
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
