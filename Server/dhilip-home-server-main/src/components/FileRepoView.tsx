import React, { useState } from 'react';
import { FileCode, Copy, Check, Folder, Eye } from 'lucide-react';
import { PROJECT_FILES, ProjectFileEntry } from '../data/projectFiles';

export const FileRepoView: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<ProjectFileEntry>(PROJECT_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [filterCategory, setFilterCategory] = useState<string>('all');

  const categories = ['all', 'core', 'route', 'service', 'database', 'deployment', 'config'];

  const filteredFiles = PROJECT_FILES.filter((f) => {
    if (filterCategory === 'all') return true;
    return f.category === filterCategory;
  });

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900">Project Codebase &amp; Repository Files</h2>
        <p className="text-slate-600 text-sm mt-1">
          Browse, inspect, and copy all generated production Python, SQLite, systemd, and bash scripts ready for Debian.
        </p>
      </div>

      {/* Category Badges */}
      <div className="flex flex-wrap gap-2">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilterCategory(cat)}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold capitalize transition-all cursor-pointer ${
              filterCategory === cat
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* File Tree List */}
        <div className="lg:col-span-4 bg-white rounded-2xl border border-slate-200 p-4 shadow-sm space-y-2">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider px-2 py-1 flex items-center gap-1.5">
            <Folder className="w-3.5 h-3.5" /> Files ({filteredFiles.length})
          </div>
          <div className="space-y-1 max-h-[580px] overflow-y-auto pr-1">
            {filteredFiles.map((file) => {
              const isSelected = file.path === selectedFile.path;
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left p-2.5 rounded-xl transition-all flex items-start gap-2.5 border ${
                    isSelected
                      ? 'bg-blue-50/90 border-blue-300 shadow-sm'
                      : 'border-transparent hover:bg-slate-50'
                  }`}
                >
                  <FileCode className={`w-4 h-4 mt-0.5 shrink-0 ${isSelected ? 'text-blue-600' : 'text-slate-400'}`} />
                  <div className="min-w-0 flex-1">
                    <div className="font-mono text-xs font-semibold text-slate-900 truncate">
                      {file.path}
                    </div>
                    <div className="text-[11px] text-slate-500 truncate mt-0.5">
                      {file.description}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Code Content Viewer */}
        <div className="lg:col-span-8 bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="font-mono font-bold text-sm text-slate-900">
                  {selectedFile.path}
                </span>
                <span className="px-2 py-0.5 text-[10px] font-bold rounded uppercase bg-slate-100 text-slate-700">
                  {selectedFile.category}
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-1">
                {selectedFile.description}
              </p>
            </div>

            <button
              onClick={handleCopy}
              className="px-3.5 py-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl flex items-center gap-1.5 shadow-sm transition-colors cursor-pointer"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied' : 'Copy Code'}
            </button>
          </div>

          <pre className="bg-slate-950 text-slate-200 rounded-xl p-4 text-xs font-mono max-h-[480px] overflow-y-auto leading-relaxed border border-slate-800">
            {selectedFile.content}
          </pre>
        </div>
      </div>
    </div>
  );
};
