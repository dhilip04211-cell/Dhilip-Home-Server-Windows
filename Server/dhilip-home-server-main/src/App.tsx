import React, { useState } from 'react';
import { Server, Activity, Terminal, FolderGit2, Cpu, HardDrive, Wifi, Radio, Copy, Check, ExternalLink } from 'lucide-react';
import { ArchitectureView } from './components/ArchitectureView';
import { ApiExplorerView } from './components/ApiExplorerView';
import { SystemMonitorView } from './components/SystemMonitorView';
import { DeploymentGuideView } from './components/DeploymentGuideView';
import { FileRepoView } from './components/FileRepoView';

type TabKey = 'architecture' | 'api' | 'telemetry' | 'deployment' | 'files';

export default function App() {
  const [activeTab, setActiveTab] = useState<TabKey>('architecture');
  const [copiedClone, setCopiedClone] = useState(false);

  const handleCopyClone = () => {
    navigator.clipboard.writeText('git clone https://github.com/your-username/dhilip-home-server.git && cd dhilip-home-server && ./deployment/install.sh');
    setCopiedClone(true);
    setTimeout(() => setCopiedClone(false), 2000);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16 gap-4">
            {/* Brand Logo & Name */}
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-blue-600 rounded-xl text-white shadow-sm shadow-blue-500/20">
                <Server className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="font-bold text-base md:text-lg text-slate-900 tracking-tight leading-tight">
                    DhilipHome Server
                  </h1>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                    v0.1.0 ONLINE
                  </span>
                </div>
                <p className="text-xs text-slate-500 hidden sm:block">
                  Debian Backend &amp; API Gateway for Android Clients
                </p>
              </div>
            </div>

            {/* Top Quick Actions */}
            <div className="flex items-center gap-2 sm:gap-3">
              <button
                onClick={handleCopyClone}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors cursor-pointer border border-slate-200"
              >
                {copiedClone ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                <span className="hidden sm:inline">{copiedClone ? 'Copied' : 'Copy Clone Command'}</span>
              </button>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex space-x-1 sm:space-x-2 border-t border-slate-100 py-1 overflow-x-auto">
            <button
              onClick={() => setActiveTab('architecture')}
              className={`px-3.5 py-2 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                activeTab === 'architecture'
                  ? 'bg-blue-50 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <Radio className="w-3.5 h-3.5" />
              <span>System Topology</span>
            </button>

            <button
              onClick={() => setActiveTab('api')}
              className={`px-3.5 py-2 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                activeTab === 'api'
                  ? 'bg-blue-50 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <Activity className="w-3.5 h-3.5" />
              <span>API Explorer</span>
            </button>

            <button
              onClick={() => setActiveTab('telemetry')}
              className={`px-3.5 py-2 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                activeTab === 'telemetry'
                  ? 'bg-blue-50 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <Cpu className="w-3.5 h-3.5" />
              <span>System Telemetry</span>
            </button>

            <button
              onClick={() => setActiveTab('deployment')}
              className={`px-3.5 py-2 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                activeTab === 'deployment'
                  ? 'bg-blue-50 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <Terminal className="w-3.5 h-3.5" />
              <span>Debian Setup</span>
            </button>

            <button
              onClick={() => setActiveTab('files')}
              className={`px-3.5 py-2 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                activeTab === 'files'
                  ? 'bg-blue-50 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              <FolderGit2 className="w-3.5 h-3.5" />
              <span>Codebase Files</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {activeTab === 'architecture' && <ArchitectureView />}
        {activeTab === 'api' && <ApiExplorerView />}
        {activeTab === 'telemetry' && <SystemMonitorView />}
        {activeTab === 'deployment' && <DeploymentGuideView />}
        {activeTab === 'files' && <FileRepoView />}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-4 mt-auto">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500">
          <div>
            <strong>DhilipHome Server</strong> &bull; Debian Computer Backend Hub
          </div>
          <div className="flex items-center gap-4">
            <span>LAN Port: 8080 (TCP)</span>
            <span>Discovery: 8888 (UDP)</span>
            <span>SQLite WAL</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
