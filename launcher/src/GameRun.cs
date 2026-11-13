using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace NoDeal.Launcher
{
    enum GameMode { Client, Server }

    /// <summary>Where the launcher expects the game's files, next to NoDeal.exe.</summary>
    static class Paths
    {
        public static readonly string AppDir = AppDomain.CurrentDomain.BaseDirectory;
        public static readonly string GameDir = Path.Combine(AppDir, "game");
        public static readonly string RuntimeDir = Path.Combine(AppDir, "runtime");
        public static readonly string LogDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"NoDeal\logs");

        public static string Jar(GameMode mode)
        {
            return Path.Combine(GameDir, mode == GameMode.Client ? "NoDeal.jar" : "NoDeal-server.jar");
        }

        /// <summary>Explorer runs an exe opened inside a zip from a temporary copy, without the rest of the zip.</summary>
        public static bool RunningFromZip()
        {
            string dir = AppDir.ToLowerInvariant();
            return dir.Contains(@"\temp") && dir.Contains(".zip");
        }
    }

    /// <summary>
    /// One run of the game or the server: starts it with the chosen Java and
    /// copies everything it prints to a log file, which is what the launcher
    /// reads to explain a crash.
    /// </summary>
    class GameRun
    {
        public readonly GameMode Mode;
        public readonly JavaInstall Java;
        public readonly Process Process;
        /// <summary>Null when the output goes to a console window instead (server --nogui).</summary>
        public readonly string LogPath;

        readonly StreamWriter log;
        readonly object logLock = new object();

        GameRun(GameMode mode, JavaInstall java, Process process, string logPath, StreamWriter log)
        {
            Mode = mode;
            Java = java;
            Process = process;
            LogPath = logPath;
            this.log = log;
        }

        public string Name
        {
            get { return Mode == GameMode.Client ? "No Deal" : L.T("El servidor", "The server"); }
        }

        /// <param name="console">Open a console window for the server's terminal mode instead of capturing its output.</param>
        public static GameRun Start(JavaInstall java, GameMode mode, IList<string> extraArgs, bool console)
        {
            var args = new List<string>();
            if (!console)
            {
                // Java writes to a pipe in the ANSI code page by default; ask for UTF-8 (17 and 18+ name it differently).
                args.Add("-Dsun.stdout.encoding=UTF-8");
                args.Add("-Dsun.stderr.encoding=UTF-8");
                args.Add("-Dstdout.encoding=UTF-8");
                args.Add("-Dstderr.encoding=UTF-8");
            }
            Directory.CreateDirectory(Paths.LogDir);
            args.Add("-XX:ErrorFile=" + Path.Combine(Paths.LogDir, "hs_err_pid%p.log"));
            if (java.Major >= 22)
                args.Add("--enable-native-access=ALL-UNNAMED"); // LWJGL and Bullet load native code
            args.Add("-jar");
            args.Add(Paths.Jar(mode));
            if (mode == GameMode.Server && L.Explicit != null && !extraArgs.Contains("--lang"))
            {
                args.Add("--lang");
                args.Add(L.Explicit);
            }
            args.AddRange(extraArgs);

            // java.exe with a hidden console, not javaw.exe: javaw reports a JVM that can't start in a modal
            // message box ("Java Virtual Machine Launcher") that blocks until clicked, instead of in the log.
            var info = new ProcessStartInfo(java.JavaExe, JoinArguments(args))
            {
                WorkingDirectory = Paths.GameDir,
                UseShellExecute = false,
                CreateNoWindow = true,
            };

            if (console)
            {
                // The launcher has no console, so cmd opens a new one. It stays open to show the help or an
                // error (bad options) instead of vanishing.
                args.Insert(0, java.JavaExe);
                bool help = extraArgs.Contains("--help") || extraArgs.Contains("-h");
                info.FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe";
                info.Arguments = "/c \"" + JoinArguments(args) + (help ? " & pause" : " || pause") + "\"";
                return new GameRun(mode, java, Process.Start(info), null, null);
            }

            info.RedirectStandardOutput = true;
            info.RedirectStandardError = true;
            info.StandardOutputEncoding = Encoding.UTF8;
            info.StandardErrorEncoding = Encoding.UTF8;

            string logPath = Path.Combine(Paths.LogDir, mode == GameMode.Client ? "cliente.log" : "servidor.log");
            var log = new StreamWriter(logPath, false, new UTF8Encoding(false)) { AutoFlush = true };
            log.WriteLine("# No Deal launcher " + BuildInfo.Version + ", " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
            log.WriteLine("# " + java.Describe() + " - " + java.JavaExe);
            log.WriteLine("# " + info.FileName + " " + info.Arguments);
            log.WriteLine();

            var process = new Process { StartInfo = info };
            var run = new GameRun(mode, java, process, logPath, log);
            process.OutputDataReceived += (sender, e) => run.Write(e.Data);
            process.ErrorDataReceived += (sender, e) => run.Write(e.Data);
            try
            {
                process.Start();
            }
            catch (Exception)
            {
                log.Dispose();
                throw;
            }
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            return run;
        }

        void Write(string line)
        {
            if (line == null)
                return;
            lock (logLock)
            {
                try { log.WriteLine(line); }
                catch (ObjectDisposedException) { }
            }
        }

        /// <summary>Waits for the game to end and for its last output to reach the log; returns the exit code.</summary>
        public int WaitForExit()
        {
            Process.WaitForExit();
            if (log != null)
                lock (logLock)
                    log.Dispose();
            return Process.ExitCode;
        }

        /// <summary>The whole log, or an empty string if there is none.</summary>
        public string ReadLog()
        {
            if (LogPath == null)
                return "";
            try
            {
                using (var stream = new FileStream(LogPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
                using (var reader = new StreamReader(stream, Encoding.UTF8))
                    return reader.ReadToEnd();
            }
            catch (Exception)
            {
                return "";
            }
        }

        /// <summary>Quotes arguments the way the Microsoft C runtime (and so java.exe) splits them.</summary>
        public static string JoinArguments(IEnumerable<string> args)
        {
            var line = new StringBuilder();
            foreach (string arg in args)
            {
                if (line.Length > 0)
                    line.Append(' ');
                if (arg.Length > 0 && arg.IndexOfAny(new[] { ' ', '\t', '"' }) < 0)
                {
                    line.Append(arg);
                    continue;
                }
                line.Append('"');
                int backslashes = 0;
                foreach (char c in arg)
                {
                    if (c == '\\')
                    {
                        backslashes++;
                        continue;
                    }
                    if (c == '"')
                        line.Append('\\', backslashes * 2 + 1);
                    else
                        line.Append('\\', backslashes);
                    backslashes = 0;
                    line.Append(c);
                }
                line.Append('\\', backslashes * 2);
                line.Append('"');
            }
            return line.ToString();
        }
    }
}
