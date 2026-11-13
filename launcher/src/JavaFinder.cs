using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using Microsoft.Win32;

namespace NoDeal.Launcher
{
    /// <summary>One Java found on this PC, usable or not.</summary>
    class JavaInstall
    {
        public string JavaExe;   // ...\bin\java.exe
        public string Home;      // null for stubs like Oracle's javapath
        public string Version;   // "17.0.21", "1.8.0_391"
        public int Major;        // 17, 8
        public string Arch;      // "x64", "x86", "arm64" or "?"
        public string Source;    // where it was found
        public bool Bundled;     // the runtime folder shipped with the game

        public bool IsSupported
        {
            // libGDX ships Windows natives for x86 and x64 only.
            get { return Major >= JavaFinder.RequiredMajor && (Arch == "x64" || Arch == "x86"); }
        }

        public string Location
        {
            get { return Home ?? Path.GetDirectoryName(JavaExe); }
        }

        /// <summary>"Java 17.0.21 (incluido)" / "Java 21.0.3 de Minecraft" / "Java 21.0.5 en C:\..."</summary>
        public string Describe()
        {
            string arch = Arch == "x64" ? "" : Arch == "x86" ? " de 32 bits" : " (" + Arch + ")";
            string where = Bundled ? " (incluido)" : Source == JavaFinder.Minecraft ? " de Minecraft" : " en " + Location;
            return "Java " + Version + arch + where;
        }

        /// <summary>Why this Java can't run the game, or null if it can.</summary>
        public string Problem()
        {
            if (Major < JavaFinder.RequiredMajor)
                return "es muy viejo";
            if (Arch == "arm64")
                return "es para ARM, el juego necesita Java x64";
            if (!IsSupported)
                return "no se pudo identificar";
            return null;
        }
    }

    class JavaSearch
    {
        /// <summary>The Java to run the game with, or null if none can.</summary>
        public JavaInstall Best;
        /// <summary>Every Java found, usable or not.</summary>
        public readonly List<JavaInstall> Found = new List<JavaInstall>();
    }

    /// <summary>
    /// Looks for Java the way a person would: the runtime that comes with the
    /// game, JAVA_HOME, the installers' registry keys, the PATH, the usual
    /// install folders and the runtimes Minecraft downloads. Versions are read
    /// from each JDK's "release" file, so nothing has to be run in most cases.
    /// </summary>
    static class JavaFinder
    {
        public const int RequiredMajor = 17;

        const string Bundled = "incluido";
        const string JavaHome = "JAVA_HOME";
        public const string Minecraft = "Minecraft";

        static readonly string[] RegistryRoots = {
            @"SOFTWARE\JavaSoft",
            @"SOFTWARE\Eclipse Adoptium",
            @"SOFTWARE\Eclipse Foundation",
            @"SOFTWARE\AdoptOpenJDK",
            @"SOFTWARE\Microsoft\JDK",
            @"SOFTWARE\Azul Systems",
            @"SOFTWARE\BellSoft",
            @"SOFTWARE\Amazon Corretto",
            @"SOFTWARE\Semeru",
        };

        static readonly string[] VendorFolders = {
            "Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK", "Microsoft", "Zulu", "Azul",
            "BellSoft", "Amazon Corretto", "Semeru", "IBM", "OpenJDK", "RedHat", "Oracle",
        };

        public static JavaSearch Search()
        {
            var candidates = new List<KeyValuePair<string, string>>(); // java.exe, source
            AddHome(candidates, Paths.RuntimeDir, Bundled);
            AddHome(candidates, Environment.GetEnvironmentVariable("JAVA_HOME"), JavaHome);
            foreach (string home in RegistryHomes())
                AddHome(candidates, home, "registro");
            foreach (string dir in PathDirectories())
                candidates.Add(new KeyValuePair<string, string>(Path.Combine(dir, "java.exe"), "PATH"));
            foreach (string home in FolderHomes())
                AddHome(candidates, home, "carpeta");
            foreach (string home in MinecraftHomes())
                AddHome(candidates, home, Minecraft);

            var search = new JavaSearch();
            var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var candidate in candidates)
            {
                string exe;
                try { exe = Path.GetFullPath(candidate.Key); }
                catch (Exception) { continue; }
                if (!seen.Add(exe))
                    continue;
                JavaInstall java = Probe(exe, candidate.Value);
                if (java != null)
                    search.Found.Add(java);
            }
            search.Best = Choose(search.Found);
            return search;
        }

        /// <summary>The runtime shipped with the game, then JAVA_HOME, then the newest one (x64 over x86).</summary>
        static JavaInstall Choose(List<JavaInstall> found)
        {
            JavaInstall best = null;
            foreach (JavaInstall java in found)
            {
                if (!java.IsSupported)
                    continue;
                if (java.Bundled || java.Source == JavaHome)
                    return java;
                if (best == null || java.Major > best.Major
                        || (java.Major == best.Major && java.Arch == "x64" && best.Arch != "x64"))
                    best = java;
            }
            return best;
        }

        static void AddHome(List<KeyValuePair<string, string>> candidates, string home, string source)
        {
            if (string.IsNullOrEmpty(home))
                return;
            try
            {
                string exe = Path.Combine(home.Trim().Trim('"'), "bin", "java.exe");
                if (File.Exists(exe))
                    candidates.Add(new KeyValuePair<string, string>(exe, source));
            }
            catch (Exception)
            {
                // malformed path in the registry or an environment variable
            }
        }

        static JavaInstall Probe(string javaExe, string source)
        {
            if (!File.Exists(javaExe))
                return null;
            var java = new JavaInstall { JavaExe = javaExe, Source = source, Bundled = source == Bundled };
            string bin = Path.GetDirectoryName(javaExe);
            if (string.Equals(Path.GetFileName(bin), "bin", StringComparison.OrdinalIgnoreCase))
                java.Home = Path.GetDirectoryName(bin);
            java.Arch = ReadArch(javaExe);
            if (java.Home != null && ReadReleaseFile(java))
                return java;
            return RunVersion(java) ? java : null;
        }

        /// <summary>Every JDK since 9 (and most 8s) has a "release" file with JAVA_VERSION="...".</summary>
        static bool ReadReleaseFile(JavaInstall java)
        {
            string file = Path.Combine(java.Home, "release");
            if (!File.Exists(file))
                return false;
            try
            {
                foreach (string line in File.ReadAllLines(file))
                {
                    Match m = Regex.Match(line, "^JAVA_VERSION=\"([^\"]+)\"");
                    if (m.Success)
                        return SetVersion(java, m.Groups[1].Value);
                }
            }
            catch (Exception)
            {
            }
            return false;
        }

        /// <summary>Runs "java -version" for Javas without a release file (old ones, PATH stubs).</summary>
        static bool RunVersion(JavaInstall java)
        {
            try
            {
                var info = new ProcessStartInfo(java.JavaExe, "-version")
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardError = true,
                    RedirectStandardOutput = true,
                };
                using (Process p = Process.Start(info))
                {
                    var stdout = p.StandardOutput.ReadToEndAsync();
                    string output = p.StandardError.ReadToEnd() + stdout.Result;
                    if (!p.WaitForExit(10000))
                        p.Kill();
                    Match m = Regex.Match(output, "version \"([^\"]+)\"");
                    if (!m.Success)
                        return false;
                    if (java.Arch == "?")
                        java.Arch = output.Contains("64-Bit") ? "x64" : "x86";
                    return SetVersion(java, m.Groups[1].Value);
                }
            }
            catch (Exception)
            {
                return false;
            }
        }

        static bool SetVersion(JavaInstall java, string version)
        {
            int major = ParseMajor(version);
            if (major <= 0)
                return false;
            java.Version = version;
            java.Major = major;
            return true;
        }

        /// <summary>"1.8.0_391" -> 8, "17.0.21" -> 17, "21" -> 21, "22-ea" -> 22.</summary>
        public static int ParseMajor(string version)
        {
            Match m = Regex.Match(version ?? "", @"^(\d+)(?:\.(\d+))?");
            if (!m.Success)
                return 0;
            int first = int.Parse(m.Groups[1].Value);
            if (first == 1 && m.Groups[2].Success)
                return int.Parse(m.Groups[2].Value);
            return first;
        }

        /// <summary>The CPU an exe is built for, from its PE header.</summary>
        static string ReadArch(string exe)
        {
            try
            {
                using (var stream = File.OpenRead(exe))
                using (var reader = new BinaryReader(stream))
                {
                    stream.Seek(0x3C, SeekOrigin.Begin);
                    int peHeader = reader.ReadInt32();
                    stream.Seek(peHeader + 4, SeekOrigin.Begin);
                    switch (reader.ReadUInt16())
                    {
                        case 0x8664: return "x64";
                        case 0x014C: return "x86";
                        case 0xAA64: return "arm64";
                    }
                }
            }
            catch (Exception)
            {
            }
            return "?";
        }

        static List<string> RegistryHomes()
        {
            var homes = new List<string>();
            foreach (RegistryHive hive in new[] { RegistryHive.LocalMachine, RegistryHive.CurrentUser })
                foreach (RegistryView view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
                {
                    try
                    {
                        using (RegistryKey baseKey = RegistryKey.OpenBaseKey(hive, view))
                            foreach (string root in RegistryRoots)
                                using (RegistryKey key = baseKey.OpenSubKey(root))
                                    if (key != null)
                                        CollectHomes(key, homes, 0);
                    }
                    catch (Exception)
                    {
                    }
                }
            return homes;
        }

        /// <summary>
        /// Each vendor nests its keys differently (JavaSoft\JDK\17\JavaHome,
        /// Eclipse Adoptium\JRE\17.0.2.8\hotspot\MSI\Path...), so this walks the
        /// whole subtree and keeps every value that looks like an install path.
        /// </summary>
        static void CollectHomes(RegistryKey key, List<string> homes, int depth)
        {
            foreach (string name in new[] { "JavaHome", "Path", "InstallationPath" })
            {
                string value = key.GetValue(name) as string;
                if (!string.IsNullOrEmpty(value))
                    homes.Add(value);
            }
            if (depth >= 5)
                return;
            foreach (string name in key.GetSubKeyNames())
            {
                try
                {
                    using (RegistryKey child = key.OpenSubKey(name))
                        if (child != null)
                            CollectHomes(child, homes, depth + 1);
                }
                catch (Exception)
                {
                }
            }
        }

        static IEnumerable<string> PathDirectories()
        {
            string path = Environment.GetEnvironmentVariable("PATH") ?? "";
            foreach (string dir in path.Split(';'))
            {
                string trimmed = dir.Trim().Trim('"');
                if (trimmed.Length > 0)
                    yield return Environment.ExpandEnvironmentVariables(trimmed);
            }
        }

        static List<string> FolderHomes()
        {
            var homes = new List<string>();
            var bases = new List<string>();
            foreach (string variable in new[] { "ProgramW6432", "ProgramFiles", "ProgramFiles(x86)" })
            {
                string dir = Environment.GetEnvironmentVariable(variable);
                if (!string.IsNullOrEmpty(dir) && !bases.Contains(dir))
                    bases.Add(dir);
            }
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            bases.Add(Path.Combine(localAppData, "Programs")); // per-user installs

            foreach (string baseDir in bases)
                foreach (string vendor in VendorFolders)
                    homes.AddRange(Subdirectories(Path.Combine(baseDir, vendor)));

            string profile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            homes.AddRange(Subdirectories(Path.Combine(profile, ".jdks"))); // IntelliJ downloads
            return homes;
        }

        /// <summary>Minecraft downloads its own Java 17/21, and many players have it.</summary>
        static List<string> MinecraftHomes()
        {
            var homes = new List<string>();
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            FindHomesBelow(Path.Combine(appData, @".minecraft\runtime"), homes, 5);
            FindHomesBelow(Path.Combine(localAppData,
                    @"Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"), homes, 5);
            string x86 = Environment.GetEnvironmentVariable("ProgramFiles(x86)");
            if (!string.IsNullOrEmpty(x86))
                FindHomesBelow(Path.Combine(x86, @"Minecraft Launcher\runtime"), homes, 5);
            return homes;
        }

        static IEnumerable<string> Subdirectories(string dir)
        {
            try
            {
                if (Directory.Exists(dir))
                    return Directory.GetDirectories(dir);
            }
            catch (Exception)
            {
            }
            return new string[0];
        }

        static void FindHomesBelow(string dir, List<string> homes, int depth)
        {
            if (depth < 0 || !Directory.Exists(dir))
                return;
            if (File.Exists(Path.Combine(dir, @"bin\java.exe")))
            {
                homes.Add(dir);
                return;
            }
            foreach (string child in Subdirectories(dir))
                FindHomesBelow(child, homes, depth - 1);
        }
    }
}
