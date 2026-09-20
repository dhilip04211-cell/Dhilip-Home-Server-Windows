import React, { useState } from 'react';
import { Play, Copy, Check, Terminal, Shield, ArrowRight } from 'lucide-react';

interface EndpointDef {
  id: string;
  name: string;
  method: 'GET' | 'POST' | 'DELETE';
  path: string;
  authRequired: boolean;
  description: string;
  defaultParams?: Record<string, string>;
  defaultBody?: string;
  mockResponse: any;
}

const ENDPOINTS: EndpointDef[] = [
  {
    id: 'health',
    name: 'Server Health Check',
    method: 'GET',
    path: '/api/health',
    authRequired: false,
    description: 'Returns server status, version, and uptime seconds.',
    mockResponse: {
      status: 'ok',
      server: 'DhilipHome Server',
      version: '0.3.3',
      timestamp: '2026-09-12T12:00:00Z',
      uptime_seconds: 14820
    }
  },
  {
    id: 'server',
    name: 'Server Information',
    method: 'GET',
    path: '/api/server',
    authRequired: false,
    description: 'Host identity, operating system, Python version, IP address, and start time.',
    mockResponse: {
      name: 'DhilipHome Server',
      version: '0.3.3',
      hostname: 'dhilip-server',
      ip: '192.168.1.100',
      port: 8080,
      os: 'Debian GNU/Linux 12 (bookworm)',
      python_version: '3.10.12',
      server_start_time: '2026-09-12T07:53:00Z',
      uptime_seconds: 14820,
      current_time: '2026-09-12T12:00:00Z'
    }
  },
  {
    id: 'discovery',
    name: 'LAN Discovery',
    method: 'GET',
    path: '/api/discovery',
    authRequired: false,
    description: 'Endpoint queried by DhilipHome Android app when discovering LAN devices.',
    mockResponse: {
      service: 'DhilipHome Server',
      version: '0.3.3',
      port: 8080,
      api: '/api',
      device_name: 'dhilip-server',
      lan_ip: '192.168.1.100',
      status: 'ONLINE'
    }
  },
  {
    id: 'system',
    name: 'System Hardware Metrics',
    method: 'GET',
    path: '/api/system',
    authRequired: false,
    description: 'Complete real-time CPU, RAM, Swap, Uptime, OS kernel, and Process telemetry.',
    mockResponse: {
      cpu: {
        usage_percent: 12.8,
        physical_cores: 4,
        logical_cores: 8,
        frequency: { current_mhz: 2400.0, min_mhz: 800.0, max_mhz: 3600.0 },
        load_average: { '1min': 0.38, '5min': 0.42, '15min': 0.31 }
      },
      memory: {
        total_bytes: 17179869184,
        used_bytes: 6871947673,
        free_bytes: 5153960755,
        available_bytes: 10307921511,
        usage_percent: 40.0,
        total_human: '16.00 GB',
        used_human: '6.40 GB',
        free_human: '4.80 GB',
        available_human: '9.60 GB'
      },
      swap: {
        total_bytes: 2147483648,
        used_bytes: 104857600,
        free_bytes: 2042626048,
        usage_percent: 4.9,
        total_human: '2.00 GB',
        used_human: '100.00 MB',
        free_human: '1.90 GB'
      },
      uptime: {
        system_uptime_seconds: 184500,
        system_uptime_human: '2d 3h 15m 0s',
        server_uptime_seconds: 14820,
        server_uptime_human: '4h 7m 0s',
        boot_time: '2026-09-10T08:45:00Z'
      },
      operating_system: {
        os_name: 'Linux',
        distribution: 'Debian GNU/Linux 12 (bookworm)',
        kernel: '6.1.0-21-amd64',
        architecture: 'x86_64',
        hostname: 'dhilip-server'
      },
      processes: {
        total_count: 146,
        running_count: 2
      }
    }
  },
  {
    id: 'storage',
    name: 'Drive & Storage Partitions',
    method: 'GET',
    path: '/api/storage',
    authRequired: false,
    description: 'Scans mounted filesystems, excludes virtual mounts, calculates usage.',
    mockResponse: {
      drives: [
        {
          mount: '/',
          device: '/dev/sda1',
          filesystem: 'ext4',
          total_bytes: 500000000000,
          used_bytes: 150000000000,
          free_bytes: 350000000000,
          usage_percent: 30.0,
          total_human: '465.66 GB',
          used_human: '139.70 GB',
          free_human: '325.96 GB'
        }
      ],
      media_storage: {
        path: '/opt/dhilip-home-server/media',
        total_bytes: 500000000000,
        used_bytes: 150000000000,
        free_bytes: 350000000000,
        usage_percent: 30.0,
        total_human: '465.66 GB',
        used_human: '139.70 GB',
        free_human: '325.96 GB'
      }
    }
  },
  {
    id: 'network',
    name: 'Network Adapters & Traffic',
    method: 'GET',
    path: '/api/network',
    authRequired: false,
    description: 'Interfaces, active LAN IPs, MAC addresses, and bytes transferred.',
    mockResponse: {
      hostname: 'dhilip-server',
      primary_ip: '192.168.1.100',
      port: 8080,
      lan_ips: ['192.168.1.100'],
      interfaces: [
        {
          name: 'eth0',
          status: 'up',
          ipv4: '192.168.1.100',
          ipv6: '2405:201:6803:4023:...',
          mac_address: 'b8:27:eb:aa:bb:cc',
          speed_mbps: 1000
        }
      ],
      io: {
        bytes_sent: 89456123,
        bytes_recv: 451239871,
        bytes_sent_human: '85.31 MB',
        bytes_recv_human: '430.34 MB',
        packets_sent: 142000,
        packets_recv: 284000
      }
    }
  },
  {
    id: 'files',
    name: 'Sandboxed File Listing',
    method: 'GET',
    path: '/api/files',
    authRequired: true,
    description: 'Lists files and folders inside MEDIA_ROOT with strict traversal protection.',
    mockResponse: {
      success: true,
      data: {
        current_path: '',
        total_directories: 3,
        total_files: 2,
        directories: [
          { name: 'Movies', path: 'Movies', is_dir: true, modified_time: '2026-09-12T10:00:00Z' },
          { name: 'Music', path: 'Music', is_dir: true, modified_time: '2026-09-12T10:05:00Z' },
          { name: 'Photos', path: 'Photos', is_dir: true, modified_time: '2026-09-12T10:10:00Z' }
        ],
        files: [
          {
            name: 'sample_video.mp4',
            path: 'sample_video.mp4',
            is_dir: false,
            size_bytes: 45283921,
            size_human: '43.19 MB',
            extension: 'mp4',
            mime_type: 'video/mp4',
            modified_time: '2026-09-12T10:15:00Z'
          }
        ]
      }
    }
  },
  {
    id: 'auth_login',
    name: 'Administrator Authentication',
    method: 'POST',
    path: '/api/auth/login',
    authRequired: false,
    description: 'Authenticates admin credentials and issues a signed bearer token.',
    defaultBody: JSON.stringify({ username: 'admin', password: 'CHANGE_THIS_PASSWORD' }, null, 2),
    mockResponse: {
      success: true,
      message: 'Login successful',
      data: {
        token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkRIVE9LRU4ifQ.eyJzdWIiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJyb2xlIjoiYWRtaW4ifQ.demo_sig',
        token_type: 'Bearer',
        expires_in: 604800,
        user: { id: 1, username: 'admin', role: 'admin' }
      }
    }
  }
];

export const ApiExplorerView: React.FC = () => {
  const [selectedEndpoint, setSelectedEndpoint] = useState<EndpointDef>(ENDPOINTS[0]);
  const [copied, setCopied] = useState(false);
  const [responseView, setResponseView] = useState<any>(selectedEndpoint.mockResponse);
  const [statusText, setStatusText] = useState<string>('200 OK');
  const [isLoading, setIsLoading] = useState(false);

  const getCurlCommand = (ep: EndpointDef) => {
    let cmd = `curl -X ${ep.method} http://192.168.1.100:8080${ep.path}`;
    if (ep.authRequired) {
      cmd += ` \\\n  -H "Authorization: Bearer <AUTH_TOKEN>"`;
    }
    if (ep.defaultBody) {
      cmd += ` \\\n  -H "Content-Type: application/json" \\\n  -d '${ep.defaultBody.replace(/\n/g, '')}'`;
    }
    return cmd;
  };

  const handleExecute = () => {
    setIsLoading(true);
    setTimeout(() => {
      setResponseView(selectedEndpoint.mockResponse);
      setStatusText('200 OK (Simulated Response)');
      setIsLoading(false);
    }, 250);
  };

  const handleCopyCurl = () => {
    navigator.clipboard.writeText(getCurlCommand(selectedEndpoint));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900">API Contract &amp; Interactive Explorer</h2>
        <p className="text-slate-600 text-sm mt-1">
          Explore and simulate every REST endpoint exposed by DhilipHome Server for the Android application.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Endpoint Selector List */}
        <div className="lg:col-span-4 bg-white rounded-2xl border border-slate-200 p-4 shadow-sm space-y-2">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider px-2 py-1">
            Available Endpoints
          </div>
          <div className="space-y-1.5 max-h-[580px] overflow-y-auto pr-1">
            {ENDPOINTS.map((ep) => {
              const isSelected = ep.id === selectedEndpoint.id;
              return (
                <button
                  key={ep.id}
                  onClick={() => {
                    setSelectedEndpoint(ep);
                    setResponseView(ep.mockResponse);
                    setStatusText('200 OK');
                  }}
                  className={`w-full text-left p-3 rounded-xl transition-all flex items-center justify-between border ${
                    isSelected
                      ? 'bg-blue-50/80 border-blue-300 shadow-sm'
                      : 'border-transparent hover:bg-slate-50'
                  }`}
                >
                  <div className="space-y-1 min-w-0 pr-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                          ep.method === 'GET'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {ep.method}
                      </span>
                      <span className="font-semibold text-xs text-slate-900 truncate">
                        {ep.name}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 font-mono truncate">
                      {ep.path}
                    </div>
                  </div>
                  {ep.authRequired && (
                    <span title="Auth Required">
                      <Shield className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* Request / Response Panel */}
        <div className="lg:col-span-8 space-y-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-5">
            {/* Header info */}
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="px-2.5 py-1 text-xs font-bold rounded-lg bg-emerald-100 text-emerald-800">
                    {selectedEndpoint.method}
                  </span>
                  <span className="font-mono text-sm font-semibold text-slate-900">
                    {selectedEndpoint.path}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {selectedEndpoint.description}
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={handleExecute}
                  disabled={isLoading}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl flex items-center gap-2 shadow-sm transition-colors cursor-pointer"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  {isLoading ? 'Sending...' : 'Test Request'}
                </button>
              </div>
            </div>

            {/* Curl Command Snippet */}
            <div>
              <div className="flex items-center justify-between text-xs font-semibold text-slate-600 mb-2">
                <span className="flex items-center gap-1.5">
                  <Terminal className="w-3.5 h-3.5" /> cURL Command
                </span>
                <button
                  onClick={handleCopyCurl}
                  className="flex items-center gap-1 text-blue-600 hover:text-blue-700 cursor-pointer"
                >
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
              <pre className="bg-slate-900 text-slate-200 rounded-xl p-3 text-xs font-mono overflow-x-auto">
                {getCurlCommand(selectedEndpoint)}
              </pre>
            </div>

            {/* Response Section */}
            <div>
              <div className="flex items-center justify-between text-xs font-semibold text-slate-600 mb-2">
                <span>Response Body</span>
                <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-mono text-[11px]">
                  {statusText}
                </span>
              </div>
              <pre className="bg-slate-950 text-emerald-400 rounded-xl p-4 text-xs font-mono max-h-80 overflow-y-auto border border-slate-800">
                {JSON.stringify(responseView, null, 2)}
              </pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
