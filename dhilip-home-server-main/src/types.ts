export interface SystemMetrics {
  cpu: {
    usage_percent: number;
    physical_cores: number;
    logical_cores: number;
    frequency?: { current_mhz: number; min_mhz: number; max_mhz: number };
    load_average?: { '1min': number; '5min': number; '15min': number };
  };
  memory: {
    total_bytes: number;
    used_bytes: number;
    free_bytes: number;
    available_bytes: number;
    usage_percent: number;
    total_human: string;
    used_human: string;
    free_human: string;
  };
  swap: {
    total_bytes: number;
    used_bytes: number;
    free_bytes: number;
    usage_percent: number;
    total_human: string;
    used_human: string;
  };
  uptime: {
    system_uptime_seconds: number;
    system_uptime_human: string;
    server_uptime_seconds: number;
    server_uptime_human: string;
    boot_time: string;
  };
  operating_system: {
    os_name: string;
    distribution: string;
    version: string;
    kernel: string;
    architecture: string;
    hostname: string;
    python_version: string;
  };
  processes: {
    total_count: number;
    running_count: number;
  };
  timestamp: string;
}

export interface DriveInfo {
  mount: string;
  device?: string;
  filesystem: string;
  total_bytes: number;
  used_bytes: number;
  free_bytes: number;
  usage_percent: number;
  total_human: string;
  used_human: string;
  free_human: string;
}

export interface NetworkInfo {
  hostname: string;
  primary_ip: string;
  port: number;
  lan_ips: string[];
  interfaces: Array<{
    name: string;
    status: string;
    ipv4: string | null;
    ipv6: string | null;
    mac_address: string | null;
    speed_mbps: number | null;
  }>;
  io: {
    bytes_sent: number;
    bytes_recv: number;
    bytes_sent_human: string;
    bytes_recv_human: string;
    packets_sent: number;
    packets_recv: number;
  };
}
