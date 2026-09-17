import React from 'react';
import { Server, Wifi, Smartphone, Tv, Radio, Database, ShieldCheck, Activity, Folder, Film, ArrowDown, Cpu, HardDrive } from 'lucide-react';

export const ArchitectureView: React.FC = () => {
  return (
    <div className="space-y-8">
      {/* Top Banner Overview */}
      <div className="bg-slate-900 text-white rounded-2xl p-6 md:p-8 shadow-xl border border-slate-800 relative overflow-hidden">
        <div className="absolute -right-12 -bottom-12 w-64 h-64 bg-blue-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="max-w-3xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/20 text-blue-300 text-xs font-semibold mb-4 border border-blue-500/30">
            <Radio className="w-3.5 h-3.5 animate-pulse text-emerald-400" />
            Debian Home LAN Architecture
          </div>
          <h2 className="text-2xl md:text-3xl font-bold tracking-tight text-white mb-3">
            DhilipHome System Topology
          </h2>
          <p className="text-slate-300 text-sm md:text-base leading-relaxed">
            The DhilipHome Server operates as an independent backend service on a Debian server machine.
            It exposes high-speed REST endpoints, WebSocket real-time telemetry, and UDP discovery to
            Android mobile clients, Android TV, and Jio devices across your home network.
          </p>
        </div>
      </div>

      {/* Visual Diagram */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 md:p-10 shadow-sm">
        <h3 className="text-lg font-bold text-slate-900 mb-8 text-center">
          Component Interaction Diagram
        </h3>

        <div className="flex flex-col items-center max-w-4xl mx-auto">
          {/* Debian Server Node */}
          <div className="w-full max-w-2xl bg-gradient-to-b from-slate-900 to-slate-950 text-white rounded-2xl p-6 shadow-xl border border-slate-800">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4 mb-4">
              <div className="flex items-center gap-3">
                <div className="p-2.5 bg-blue-600/20 rounded-xl border border-blue-500/30 text-blue-400">
                  <Server className="w-6 h-6" />
                </div>
                <div>
                  <div className="font-bold text-base text-white">Debian Server (dhilip-server)</div>
                  <div className="text-xs text-slate-400">Host: 0.0.0.0:8080 &bull; Python 3 &bull; systemd</div>
                </div>
              </div>
              <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                Daemon Active
              </span>
            </div>

            {/* Modules Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-blue-400 font-semibold mb-1">
                  <Activity className="w-3.5 h-3.5" /> REST API
                </div>
                <div className="text-slate-400">/api/v1 endpoints</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-purple-400 font-semibold mb-1">
                  <Wifi className="w-3.5 h-3.5" /> WebSocket
                </div>
                <div className="text-slate-400">Flask-SocketIO 3s</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-emerald-400 font-semibold mb-1">
                  <Cpu className="w-3.5 h-3.5" /> System Monitor
                </div>
                <div className="text-slate-400">psutil hardware</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-amber-400 font-semibold mb-1">
                  <HardDrive className="w-3.5 h-3.5" /> Storage
                </div>
                <div className="text-slate-400">Mount partition scan</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-cyan-400 font-semibold mb-1">
                  <Folder className="w-3.5 h-3.5" /> File Manager
                </div>
                <div className="text-slate-400">Sandboxed /media</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-pink-400 font-semibold mb-1">
                  <Film className="w-3.5 h-3.5" /> Media Server
                </div>
                <div className="text-slate-400">HTTP 206 Range stream</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-indigo-400 font-semibold mb-1">
                  <ShieldCheck className="w-3.5 h-3.5" /> Security
                </div>
                <div className="text-slate-400">PBKDF2 &amp; Tokens</div>
              </div>

              <div className="bg-slate-800/80 rounded-xl p-3 border border-slate-700/60">
                <div className="flex items-center gap-1.5 text-orange-400 font-semibold mb-1">
                  <Database className="w-3.5 h-3.5" /> SQLite WAL
                </div>
                <div className="text-slate-400">dhiliphome.db</div>
              </div>
            </div>
          </div>

          {/* Network Bus Connector */}
          <div className="flex flex-col items-center my-3">
            <div className="w-0.5 h-6 bg-slate-300"></div>
            <div className="flex items-center gap-2 px-4 py-1.5 rounded-full bg-slate-100 border border-slate-300 text-slate-700 text-xs font-semibold shadow-sm">
              <Wifi className="w-3.5 h-3.5 text-blue-600" />
              <span>LAN / Wi-Fi Router Subnet (e.g. 192.168.1.0/24)</span>
              <span className="text-[10px] bg-slate-200 px-1.5 py-0.5 rounded text-slate-600">UDP:8888</span>
            </div>
            <div className="w-0.5 h-6 bg-slate-300"></div>
          </div>

          {/* Client Devices */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 w-full">
            {/* Android Mobile */}
            <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 flex items-start gap-3 hover:border-blue-400 transition-colors">
              <div className="p-2.5 bg-blue-100 rounded-lg text-blue-700">
                <Smartphone className="w-5 h-5" />
              </div>
              <div>
                <div className="font-semibold text-slate-900 text-sm">Android Mobile</div>
                <div className="text-xs font-medium text-blue-600">DhilipHome APK</div>
                <p className="text-[11px] text-slate-500 mt-1">
                  Connects via REST &amp; WebSocket. Queries system metrics, browses files, and triggers media scans.
                </p>
              </div>
            </div>

            {/* Android TV */}
            <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 flex items-start gap-3 hover:border-purple-400 transition-colors">
              <div className="p-2.5 bg-purple-100 rounded-lg text-purple-700">
                <Tv className="w-5 h-5" />
              </div>
              <div>
                <div className="font-semibold text-slate-900 text-sm">Android TV</div>
                <div className="text-xs font-medium text-purple-600">DhilipHome TV App</div>
                <p className="text-[11px] text-slate-500 mt-1">
                  ExoPlayer video streaming using HTTP 206 Range headers. Smooth 4K/1080p seeking without memory bloat.
                </p>
              </div>
            </div>

            {/* Jio Device */}
            <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 flex items-start gap-3 hover:border-emerald-400 transition-colors">
              <div className="p-2.5 bg-emerald-100 rounded-lg text-emerald-700">
                <Radio className="w-5 h-5" />
              </div>
              <div>
                <div className="font-semibold text-slate-900 text-sm">Jio Device</div>
                <div className="text-xs font-medium text-emerald-600">JioFiber Set-Top Box</div>
                <p className="text-[11px] text-slate-500 mt-1">
                  Discovers server over UDP 8888 and plays shared home media, family photos, and music libraries.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
