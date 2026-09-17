import React, { useState } from 'react';
import { Terminal, Copy, Check, ShieldCheck, RefreshCw, Play, AlertTriangle } from 'lucide-react';

export const DeploymentGuideView: React.FC = () => {
  const [copiedSection, setCopiedSection] = useState<string | null>(null);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSection(id);
    setTimeout(() => setCopiedSection(null), 2000);
  };

  const installCommands = `# 1. Clone repository on your Debian computer
git clone https://github.com/<your-username>/dhilip-home-server.git
cd dhilip-home-server

# 2. Grant script permissions
chmod +x deployment/*.sh

# 3. Run Debian installer (handles python3-venv, requirements.txt, .env, and SQLite)
sudo ./deployment/install.sh

# 4. Enable and start as background systemd service
sudo cp deployment/dhilip-home-server.service /etc/systemd/system/
sudo sed -i "s|/opt/dhilip-home-server|$(pwd)|g" /etc/systemd/system/dhilip-home-server.service
sudo systemctl daemon-reload
sudo systemctl enable dhilip-home-server
sudo systemctl start dhilip-home-server`;

  const controlCommands = `# Check service status
sudo systemctl status dhilip-home-server

# View live streaming daemon logs
sudo journalctl -u dhilip-home-server -f

# Restart after changes
sudo systemctl restart dhilip-home-server

# Pull updates from GitHub safely
./deployment/update.sh`;

  const firewallCommands = `# Allow port 8080 restricted to LAN devices only (e.g. 192.168.1.0/24 subnet)
sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp

# Allow UDP discovery port 8888 for Android broadcast
sudo ufw allow from 192.168.1.0/24 to any port 8888 proto udp

# Reload UFW
sudo ufw reload`;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900">Debian Server Deployment &amp; Service Control</h2>
        <p className="text-slate-600 text-sm mt-1">
          Complete CLI instructions for installing, configuring, and operating DhilipHome Server as a systemd daemon on Debian.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6">
        {/* Step 1: Automated Installation */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-bold">
                1
              </span>
              <h3 className="font-bold text-base text-slate-900">
                Installation on Debian Computer
              </h3>
            </div>
            <button
              onClick={() => copyToClipboard(installCommands, 'install')}
              className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 cursor-pointer"
            >
              {copiedSection === 'install' ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
              {copiedSection === 'install' ? 'Copied' : 'Copy Commands'}
            </button>
          </div>
          <p className="text-xs text-slate-600">
            Executes the self-contained <code className="font-mono text-blue-600">install.sh</code>, which verifies Python 3, sets up a virtual environment (<code className="font-mono">venv</code>), installs dependencies, creates runtime folders, and seeds the SQLite database.
          </p>
          <pre className="bg-slate-950 text-slate-200 rounded-xl p-4 text-xs font-mono overflow-x-auto leading-relaxed border border-slate-800">
            {installCommands}
          </pre>
        </div>

        {/* Step 2: Systemd Daemon Management */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-bold">
                2
              </span>
              <h3 className="font-bold text-base text-slate-900">
                Systemd Service Control &amp; Logs
              </h3>
            </div>
            <button
              onClick={() => copyToClipboard(controlCommands, 'control')}
              className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 cursor-pointer"
            >
              {copiedSection === 'control' ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
              {copiedSection === 'control' ? 'Copied' : 'Copy Commands'}
            </button>
          </div>
          <p className="text-xs text-slate-600">
            DhilipHome Server runs automatically in the background on system boot with <code className="font-mono">Restart=always</code> and sandboxed file permissions.
          </p>
          <pre className="bg-slate-950 text-slate-200 rounded-xl p-4 text-xs font-mono overflow-x-auto leading-relaxed border border-slate-800">
            {controlCommands}
          </pre>
        </div>

        {/* Step 3: Firewall UFW Rules */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-bold">
                3
              </span>
              <h3 className="font-bold text-base text-slate-900">
                UFW Firewall LAN Hardening
              </h3>
            </div>
            <button
              onClick={() => copyToClipboard(firewallCommands, 'firewall')}
              className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 cursor-pointer"
            >
              {copiedSection === 'firewall' ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
              {copiedSection === 'firewall' ? 'Copied' : 'Copy Commands'}
            </button>
          </div>
          <div className="flex items-start gap-3 p-3 bg-amber-50 rounded-xl border border-amber-200/80 text-amber-800 text-xs">
            <AlertTriangle className="w-4 h-4 shrink-0 text-amber-600 mt-0.5" />
            <span>
              <strong>Security Best Practice:</strong> Only allow port 8080 from your trusted home subnet (e.g. 192.168.1.0/24). Do not forward port 8080 directly on your home Internet router without a VPN or reverse proxy.
            </span>
          </div>
          <pre className="bg-slate-950 text-slate-200 rounded-xl p-4 text-xs font-mono overflow-x-auto leading-relaxed border border-slate-800">
            {firewallCommands}
          </pre>
        </div>
      </div>
    </div>
  );
};
