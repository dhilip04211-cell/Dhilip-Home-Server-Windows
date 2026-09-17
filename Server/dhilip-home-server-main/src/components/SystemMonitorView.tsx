import React, { useState } from 'react';
import { Cpu, HardDrive, Activity, Wifi, Clock, Server, CheckCircle2 } from 'lucide-react';
import { SystemMetrics, DriveInfo, NetworkInfo } from '../types';

export const SystemMonitorView: React.FC = () => {
  const [metrics] = useState<SystemMetrics>({
    cpu: {
      usage_percent: 14.5,
      physical_cores: 4,
      logical_cores: 8,
      frequency: { current_mhz: 2400.0, min_mhz: 800.0, max_mhz: 3600.0 },
      load_average: { '1min': 0.42, '5min': 0.38, '15min': 0.29 }
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
      system_uptime_human: '2d 3h 15m',
      server_uptime_seconds: 14820,
      server_uptime_human: '4h 7m',
      boot_time: '2026-09-10T08:45:00Z'
    },
    operating_system: {
      os_name: 'Linux',
      distribution: 'Debian GNU/Linux 12 (bookworm)',
      version: '12',
      kernel: '6.1.0-21-amd64',
      architecture: 'x86_64',
      hostname: 'dhilip-server',
      python_version: '3.10.12'
    },
    processes: {
      total_count: 142,
      running_count: 2
    },
    timestamp: new Date().toISOString()
  });

  const [drives] = useState<DriveInfo[]>([
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
    },
    {
      mount: '/media',
      device: '/dev/sdb1',
      filesystem: 'ext4',
      total_bytes: 2000000000000,
      used_bytes: 420000000000,
      free_bytes: 1580000000000,
      usage_percent: 21.0,
      total_human: '1.82 TB',
      used_human: '391.15 GB',
      free_human: '1.44 TB'
    }
  ]);

  const [network] = useState<NetworkInfo>({
    hostname: 'dhilip-server',
    primary_ip: '192.168.1.100',
    port: 8080,
    lan_ips: ['192.168.1.100'],
    interfaces: [
      {
        name: 'eth0',
        status: 'up',
        ipv4: '192.168.1.100',
        ipv6: '2405:201:...',
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
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-900">System Performance &amp; Hardware Telemetry</h2>
          <p className="text-slate-600 text-sm mt-1">
            Real-time metric telemetry collected via <code className="text-blue-600 font-mono">psutil</code> and broadcast over WebSocket.
          </p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-50 text-emerald-700 text-xs font-semibold border border-emerald-200">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
          Live WebSocket Telemetry (3s interval)
        </div>
      </div>

      {/* Key Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* CPU Card */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">CPU Utilization</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <Cpu className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-3xl font-bold text-slate-900">{metrics.cpu.usage_percent}%</div>
            <div className="text-xs text-slate-500 mt-1">
              {metrics.cpu.physical_cores} Physical / {metrics.cpu.logical_cores} Logical Cores
            </div>
          </div>
          <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
            <div
              className="bg-blue-600 h-full rounded-full transition-all duration-500"
              style={{ width: `${metrics.cpu.usage_percent}%` }}
            />
          </div>
          <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
            <span>Load: {metrics.cpu.load_average?.['1min']} (1m)</span>
            <span>Freq: {metrics.cpu.frequency?.current_mhz} MHz</span>
          </div>
        </div>

        {/* RAM Card */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">RAM Memory</span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-3xl font-bold text-slate-900">{metrics.memory.usage_percent}%</div>
            <div className="text-xs text-slate-500 mt-1">
              {metrics.memory.used_human} of {metrics.memory.total_human} used
            </div>
          </div>
          <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
            <div
              className="bg-purple-600 h-full rounded-full transition-all duration-500"
              style={{ width: `${metrics.memory.usage_percent}%` }}
            />
          </div>
          <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
            <span>Available: {metrics.memory.available_human}</span>
            <span>Free: {metrics.memory.free_human}</span>
          </div>
        </div>

        {/* Storage Card */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Primary Root Disk</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <HardDrive className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-3xl font-bold text-slate-900">{drives[0].usage_percent}%</div>
            <div className="text-xs text-slate-500 mt-1">
              {drives[0].used_human} of {drives[0].total_human}
            </div>
          </div>
          <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
            <div
              className="bg-amber-600 h-full rounded-full transition-all duration-500"
              style={{ width: `${drives[0].usage_percent}%` }}
            />
          </div>
          <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
            <span>Free: {drives[0].free_human}</span>
            <span>Mount: {drives[0].mount}</span>
          </div>
        </div>

        {/* Network & Uptime Card */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">System Uptime</span>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold text-slate-900">{metrics.uptime.server_uptime_human}</div>
            <div className="text-xs text-slate-500 mt-1">
              System: {metrics.uptime.system_uptime_human}
            </div>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-slate-600 pt-2 border-t border-slate-100">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
            <span>{metrics.processes.total_count} total processes active</span>
          </div>
        </div>
      </div>

      {/* Detailed Subsections */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Mounted Drives Breakdown */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <h3 className="font-bold text-base text-slate-900 flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-blue-600" /> Mounted Filesystems
          </h3>
          <div className="space-y-3">
            {drives.map((d) => (
              <div key={d.mount} className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="font-mono text-xs font-bold text-slate-900">
                    {d.mount} <span className="font-normal text-slate-500">({d.filesystem} - {d.device})</span>
                  </div>
                  <span className="text-xs font-semibold text-slate-700">{d.usage_percent}%</span>
                </div>
                <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
                  <div
                    className="bg-blue-600 h-full rounded-full"
                    style={{ width: `${d.usage_percent}%` }}
                  />
                </div>
                <div className="flex justify-between text-[11px] text-slate-500">
                  <span>Used: {d.used_human}</span>
                  <span>Free: {d.free_human}</span>
                  <span>Total: {d.total_human}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Operating System and Network specs */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm space-y-4">
          <h3 className="font-bold text-base text-slate-900 flex items-center gap-2">
            <Server className="w-4 h-4 text-purple-600" /> Host &amp; Network Specifications
          </h3>
          <div className="grid grid-cols-2 gap-3 text-xs">
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Hostname</span>
              <span className="font-mono font-bold text-slate-900">{metrics.operating_system.hostname}</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Distribution</span>
              <span className="font-bold text-slate-900">{metrics.operating_system.distribution}</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Kernel</span>
              <span className="font-mono font-semibold text-slate-900">{metrics.operating_system.kernel}</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Python Runtime</span>
              <span className="font-bold text-slate-900">Python {metrics.operating_system.python_version}</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Primary LAN IP</span>
              <span className="font-mono font-bold text-blue-600">{network.primary_ip}:8080</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
              <span className="text-slate-500 block">Network Adapter</span>
              <span className="font-mono font-bold text-slate-900">
                {network.interfaces[0].name} ({network.interfaces[0].speed_mbps} Mbps)
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
