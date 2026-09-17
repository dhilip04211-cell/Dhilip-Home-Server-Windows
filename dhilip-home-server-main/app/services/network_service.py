"""
DhilipHome Server - Network Monitor Service
Provides network interface discovery, IP addressing, and transfer statistics.
"""

import socket
import psutil
from app.utils.config import Config, format_bytes, get_lan_ip


class NetworkService:
    """Service for querying network configuration, adapters, and I/O metrics."""

    @classmethod
    def get_network_info(cls) -> dict:
        """
        Gather network interfaces, IP addresses, statuses, and throughput stats.
        Conforms to Section 9 of DhilipHome Server specification.
        """
        hostname = socket.gethostname()
        primary_ip = get_lan_ip()

        interfaces_data = []
        all_lan_ips = set()

        try:
            net_addrs = psutil.net_if_addrs()
        except Exception:
            net_addrs = {}

        try:
            net_stats = psutil.net_if_stats()
        except Exception:
            net_stats = {}

        for iface_name, addrs in net_addrs.items():
            ipv4_list = []
            ipv6_list = []
            mac_addr = None

            for addr in addrs:
                # AF_INET = IPv4
                if addr.family == socket.AF_INET:
                    if addr.address and not addr.address.startswith("127."):
                        ipv4_list.append(addr.address)
                        all_lan_ips.add(addr.address)
                    elif addr.address:
                        ipv4_list.append(addr.address)
                # AF_INET6 = IPv6
                elif addr.family == socket.AF_INET6:
                    # Filter out fe80 link-local scope or keep full address
                    if addr.address:
                        ipv6_clean = addr.address.split("%")[0]
                        ipv6_list.append(ipv6_clean)
                # AF_LINK / MAC address (family integer or 17 on Linux)
                elif addr.family in (psutil.AF_LINK, getattr(socket, "AF_PACKET", 17)):
                    mac_addr = addr.address

            status = "unknown"
            speed = 0
            if iface_name in net_stats:
                st = net_stats[iface_name]
                status = "up" if st.isup else "down"
                speed = st.speed  # Speed in Mbps

            # Do not expose loopback in primary list unless it's the only one
            interfaces_data.append({
                "name": iface_name,
                "status": status,
                "ipv4": ipv4_list[0] if ipv4_list else None,
                "all_ipv4": ipv4_list,
                "ipv6": ipv6_list[0] if ipv6_list else None,
                "all_ipv6": ipv6_list,
                "mac_address": mac_addr,
                "speed_mbps": speed if speed > 0 else None,
            })

        # Net I/O Counters
        io_info = {}
        try:
            io = psutil.net_io_counters()
            io_info = {
                "bytes_sent": io.bytes_sent,
                "bytes_recv": io.bytes_recv,
                "bytes_sent_human": format_bytes(io.bytes_sent),
                "bytes_recv_human": format_bytes(io.bytes_recv),
                "packets_sent": io.packets_sent,
                "packets_recv": io.packets_recv,
                "errin": io.errin,
                "errout": io.errout,
                "dropin": io.dropin,
                "dropout": io.dropout,
            }
        except Exception:
            io_info = {
                "bytes_sent": 0,
                "bytes_recv": 0,
                "bytes_sent_human": "0 B",
                "bytes_recv_human": "0 B",
            }

        return {
            "hostname": hostname,
            "primary_ip": primary_ip,
            "port": Config.PORT,
            "lan_ips": sorted(list(all_lan_ips)) if all_lan_ips else [primary_ip],
            "interfaces": interfaces_data,
            "io": io_info,
        }
