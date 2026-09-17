import os, sys, time, socket, subprocess, threading, webbrowser, tkinter as tk
from tkinter import ttk, messagebox
from pathlib import Path

APP_NAME = 'Dhilip Home Server'
VERSION = '0.3.1'
PORT = int(os.getenv('PORT', '8080'))

class App:
    def __init__(self, root):
        self.root = root
        self.root.title(f'{APP_NAME} {VERSION}')
        self.root.geometry('620x430')
        self.root.minsize(560, 390)
        self.root.protocol('WM_DELETE_WINDOW', self.close)
        self.proc = None
        self.build_ui()
        self.root.after(700, self.refresh)
        self.start_server()

    def lan_ip(self):
        try:
            s=socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(.5)
            s.connect(('8.8.8.8',80)); ip=s.getsockname()[0]; s.close(); return ip
        except Exception:
            try: return socket.gethostbyname(socket.gethostname())
            except Exception: return '127.0.0.1'

    def build_ui(self):
        outer=ttk.Frame(self.root,padding=22); outer.pack(fill='both',expand=True)
        ttk.Label(outer,text='DHILIP HOME SERVER',font=('Segoe UI',22,'bold')).pack(anchor='w')
        ttk.Label(outer,text=f'Private home server • Version {VERSION}',font=('Segoe UI',10)).pack(anchor='w',pady=(2,18))
        card=ttk.LabelFrame(outer,text='Server Status',padding=16); card.pack(fill='x')
        self.status=ttk.Label(card,text='Starting…',font=('Segoe UI',14,'bold')); self.status.grid(row=0,column=0,sticky='w',columnspan=2)
        self.ip=ttk.Label(card,text='LAN: detecting…'); self.ip.grid(row=1,column=0,sticky='w',pady=(12,0))
        self.local=ttk.Label(card,text=f'Local: http://127.0.0.1:{PORT}'); self.local.grid(row=2,column=0,sticky='w')
        self.url=ttk.Label(card,text=''); self.url.grid(row=3,column=0,sticky='w')
        btns=ttk.Frame(outer); btns.pack(fill='x',pady=20)
        self.start_btn=ttk.Button(btns,text='Start Server',command=self.start_server); self.start_btn.pack(side='left',padx=(0,8))
        self.stop_btn=ttk.Button(btns,text='Stop Server',command=self.stop_server); self.stop_btn.pack(side='left',padx=8)
        ttk.Button(btns,text='Open Dashboard',command=self.open_dashboard).pack(side='left',padx=8)
        ttk.Button(btns,text='Open Server Folder',command=self.open_folder).pack(side='left',padx=8)
        info=ttk.LabelFrame(outer,text='Android / TV Connection',padding=14); info.pack(fill='x')
        ttk.Label(info,text='Use the LAN address shown above. The existing Android API remains on port 8080.').pack(anchor='w')
        self.log=ttk.Label(outer,text='Ready',font=('Segoe UI',9)); self.log.pack(anchor='w',pady=(18,0))

    def start_server(self):
        if self.proc and self.proc.poll() is None: return
        try:
            args=[sys.executable,'--server']
            flags=0x08000000 if os.name=='nt' else 0
            self.proc=subprocess.Popen(args,creationflags=flags,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,cwd=str(Path(sys.executable).resolve().parent))
            self.log.config(text='Server process started')
        except Exception as e:
            messagebox.showerror(APP_NAME,f'Unable to start server:\n{e}')

    def stop_server(self):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try: self.proc.wait(timeout=4)
            except subprocess.TimeoutExpired: self.proc.kill()
        self.proc=None; self.log.config(text='Server stopped')

    def refresh(self):
        ip=self.lan_ip(); self.ip.config(text=f'LAN: http://{ip}:{PORT}'); self.url.config(text=f'Health: http://{ip}:{PORT}/api/health')
        running=self.proc is not None and self.proc.poll() is None
        self.status.config(text='● SERVER ONLINE' if running else '● SERVER STOPPED')
        self.start_btn.config(state='disabled' if running else 'normal'); self.stop_btn.config(state='normal' if running else 'disabled')
        self.root.after(1000,self.refresh)

    def open_dashboard(self): webbrowser.open(f'http://127.0.0.1:{PORT}')
    def open_folder(self): os.startfile(str(Path(sys.executable).resolve().parent)) if os.name=='nt' else None
    def close(self):
        self.stop_server(); self.root.destroy()

def run_gui():
    root=tk.Tk(); App(root); root.mainloop()

if __name__ == '__main__':
    if '--server' in sys.argv:
        from server import run_server
        run_server()
    else:
        run_gui()
