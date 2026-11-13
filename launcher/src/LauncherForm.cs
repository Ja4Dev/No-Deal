using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

namespace NoDeal.Launcher
{
    /// <summary>
    /// The launcher window: the menu's starfield and title, "Jugar",
    /// "Servidor dedicado", and which Java will be used. While the game is
    /// opening it says so; once its window appears the launcher hides, and it
    /// comes back only if the game crashes, to explain why.
    /// </summary>
    class LauncherForm : Form
    {
        const int Width96 = 760, Height96 = 440, Footer96 = 44;

        readonly Image background = LoadImage("NoDeal.background.png");
        readonly Image title = LoadImage("NoDeal.title.png");
        readonly Button playButton;
        readonly Button serverButton;
        readonly Label statusLabel;
        readonly System.Windows.Forms.Timer watchTimer;

        JavaSearch search;
        GameRun running;
        bool hiddenForGame;

        public LauncherForm()
        {
            SuspendLayout();
            AutoScaleDimensions = new SizeF(96F, 96F);
            AutoScaleMode = AutoScaleMode.Dpi;
            Text = "No Deal";
            Icon = Theme.AppIcon();
            ClientSize = new Size(Width96, Height96);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Theme.Background;
            ForeColor = Theme.Text;
            Font = new Font("Segoe UI", 9.75F);
            DoubleBuffered = true;

            playButton = Theme.MakeButton(L.T("JUGAR", "PLAY"), true);
            playButton.AutoSize = false;
            playButton.Font = new Font("Segoe UI", 18F, FontStyle.Bold);
            playButton.Bounds = new Rectangle((Width96 - 280) / 2, 200, 280, 62);
            playButton.Enabled = false;
            playButton.Click += (sender, e) => Launch(GameMode.Client);

            serverButton = Theme.MakeButton(L.T("Servidor dedicado", "Dedicated server"), false);
            serverButton.AutoSize = false;
            serverButton.Font = new Font("Segoe UI", 11F);
            serverButton.Bounds = new Rectangle((Width96 - 280) / 2, 276, 280, 40);
            serverButton.Enabled = false;
            serverButton.Click += (sender, e) => Launch(GameMode.Server);

            statusLabel = new Label
            {
                AutoSize = false,
                AutoEllipsis = true,
                Bounds = new Rectangle(16, Height96 - Footer96, Width96 - 150, Footer96),
                TextAlign = ContentAlignment.MiddleLeft,
                BackColor = Color.Transparent,
                ForeColor = Theme.Dim,
                Text = L.T("Buscando Java...", "Looking for Java..."),
            };

            var logsLink = new LinkLabel
            {
                Text = "Logs",
                AutoSize = false,
                Bounds = new Rectangle(Width96 - 130, Height96 - Footer96, 50, Footer96),
                TextAlign = ContentAlignment.MiddleRight,
                BackColor = Color.Transparent,
                LinkColor = Theme.Dim,
                ActiveLinkColor = Color.White,
                LinkBehavior = LinkBehavior.HoverUnderline,
            };
            logsLink.LinkClicked += (sender, e) => LaunchFlow.OpenLogFolder();

            var versionLabel = new Label
            {
                Text = "v" + BuildInfo.Version,
                AutoSize = false,
                Bounds = new Rectangle(Width96 - 76, Height96 - Footer96, 60, Footer96),
                TextAlign = ContentAlignment.MiddleRight,
                BackColor = Color.Transparent,
                ForeColor = Theme.Dim,
            };

            Controls.Add(playButton);
            Controls.Add(serverButton);
            Controls.Add(statusLabel);
            Controls.Add(logsLink);
            Controls.Add(versionLabel);

            watchTimer = new System.Windows.Forms.Timer { Interval = 250 };
            watchTimer.Tick += (sender, e) => WatchGame();
            ResumeLayout(false);
        }

        static Image LoadImage(string name)
        {
            using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
            {
                if (stream == null)
                    return null;
                using (Image image = Image.FromStream(stream))
                    return new Bitmap(image); // a copy that doesn't need the stream
            }
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            // Running "java -version" on old Javas can take a moment: search off the UI thread.
            ThreadPool.QueueUserWorkItem(state =>
            {
                JavaSearch result = JavaFinder.Search();
                BeginInvoke(new Action(() =>
                {
                    search = result;
                    ShowJavaStatus();
                    playButton.Enabled = serverButton.Enabled = true;
                    playButton.Focus();
                }));
            });
        }

        void ShowJavaStatus()
        {
            if (search.Best != null)
            {
                statusLabel.ForeColor = Theme.Dim;
                statusLabel.Text = search.Best.Describe();
            }
            else
            {
                statusLabel.ForeColor = Theme.Yellow;
                statusLabel.Text = L.T("Falta Java " + JavaFinder.RequiredMajor + " o más nuevo: tocá Jugar para ver cómo instalarlo",
                        "Java " + JavaFinder.RequiredMajor + " or newer is missing: press Play to see how to install it");
            }
        }

        void Launch(GameMode mode)
        {
            if (running != null || !LaunchFlow.CheckFiles(this, mode))
                return;
            JavaInstall java = LaunchFlow.EnsureJava(this, ref search);
            ShowJavaStatus();
            if (java == null)
                return;
            GameRun run = LaunchFlow.Start(this, java, mode, new string[0], false);
            if (run == null)
                return;
            running = run;
            hiddenForGame = false;
            playButton.Enabled = serverButton.Enabled = false;
            statusLabel.ForeColor = Theme.Green;
            statusLabel.Text = mode == GameMode.Client ? L.T("Abriendo No Deal...", "Opening No Deal...")
                    : L.T("Abriendo el servidor...", "Opening the server...");
            UseWaitCursor = true;
            watchTimer.Start();
        }

        /// <summary>Hides the launcher when the game's window shows up, and handles the game closing.</summary>
        void WatchGame()
        {
            Process process = running.Process;
            if (process.HasExited)
            {
                watchTimer.Stop();
                GameRun run = running;
                running = null;
                int code = run.WaitForExit();
                if (code == 0 && hiddenForGame)
                {
                    Close(); // played and quit normally: done
                    return;
                }
                UseWaitCursor = false;
                playButton.Enabled = serverButton.Enabled = true;
                ShowJavaStatus();
                if (hiddenForGame)
                    Show();
                hiddenForGame = false;
                if (code != 0 && LaunchFlow.ShowCrash(this, run, code))
                    Launch(run.Mode);
                return;
            }
            if (!hiddenForGame)
            {
                process.Refresh();
                if (process.MainWindowHandle != IntPtr.Zero)
                {
                    hiddenForGame = true;
                    UseWaitCursor = false;
                    Hide();
                }
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            int w = ClientSize.Width, h = ClientSize.Height;
            float scale = w / (float) Width96;

            if (background != null)
            {
                // Cover the window, cropping whatever overflows.
                float cover = Math.Max(w / (float) background.Width, h / (float) background.Height);
                float bw = background.Width * cover, bh = background.Height * cover;
                g.DrawImage(background, (w - bw) / 2, (h - bh) / 2, bw, bh);
            }
            using (var shade = new SolidBrush(Color.FromArgb(70, 0, 0, 0)))
                g.FillRectangle(shade, 0, 0, w, h);

            if (title != null)
            {
                float tw = w * 0.78f, th = tw * title.Height / title.Width;
                g.DrawImage(title, (w - tw) / 2, 64 * scale, tw, th);
            }

            float footer = Footer96 * scale;
            using (var band = new SolidBrush(Color.FromArgb(215, Theme.Background)))
                g.FillRectangle(band, 0, h - footer, w, footer);
            using (var line = new Pen(Theme.Border))
                g.DrawLine(line, 0, h - footer, w, h - footer);
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                watchTimer.Dispose();
                if (background != null)
                    background.Dispose();
                if (title != null)
                    title.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
