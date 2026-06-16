// Unified Javascript Bridge for Tetrium Motion Control

// 1. Navigation Setup
document.addEventListener('DOMContentLoaded', () => {
    // Fix bottom navigation links
    const navButtons = document.querySelectorAll('nav button, nav a');
    if (navButtons.length >= 4) {
        // Assume order: Dashboard, Control, Logs, Settings
        navButtons[0].onclick = () => window.location.href = 'index.html';
        navButtons[1].onclick = () => window.location.href = 'motion_control_1.html';
        navButtons[2].onclick = () => window.location.href = 'history_and_logs.html';
        navButtons[3].onclick = () => window.location.href = 'advanced_settings.html';
        
        navButtons.forEach(btn => {
            btn.style.cursor = 'pointer';
            if (btn.tagName.toLowerCase() === 'a') btn.href = '#';
        });
    }

    // Fix header online/offline status
    const statusSpan = document.querySelector('header span.font-label-caps.text-label-caps');
    const pulseDot = document.querySelector('header .status-pulse');
    
    window.updateTelemetry = function(state) {
        // state = { connected: bool, position: int, speed: int, running: bool, runtime: int }
        if (statusSpan) {
            statusSpan.innerText = state.connected ? "SYSTEM ONLINE" : "OFFLINE";
            statusSpan.className = "font-label-caps text-label-caps " + (state.connected ? "text-on-surface-variant" : "text-error");
        }
        if (pulseDot) {
            pulseDot.className = "w-2 h-2 rounded-full " + (state.connected ? "bg-secondary status-pulse" : "bg-error");
        }
        
        // Update Runtime if available
        const runtimeEl = document.getElementById('runtime');
        if (runtimeEl && state.runtime !== undefined) {
            let h = Math.floor(state.runtime / 3600);
            let m = Math.floor((state.runtime % 3600) / 60);
            let s = state.runtime % 60;
            runtimeEl.innerText = [h, m, s].map(v => String(v).padStart(2, '0')).join(':');
        }

        // Update position displays
        const posDisplay2 = document.querySelector('.font-data-lg.text-primary');
        if (posDisplay2 && posDisplay2.innerText.includes('POS')) {
            posDisplay2.innerText = 'POS: ' + (state.position > 0 ? '+' : '') + state.position.toString().padStart(4, '0') + '.00';
        }
        
        // Main position display (Dashboard)
        const posDisplayMain = document.querySelector('.font-data-lg.text-\\[48px\\]');
        if (posDisplayMain) {
            posDisplayMain.innerText = state.position;
        }
    };

    // Request initial state
    if (window.Android && window.Android.requestState) {
        window.Android.requestState();
    }

    // 2. Connection Modal
    const sensorsIcon = document.querySelector('header span.material-symbols-outlined:last-child');
    if (sensorsIcon) {
        sensorsIcon.style.cursor = 'pointer';
        
        const modal = document.createElement('div');
        modal.className = 'fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] hidden flex items-center justify-center p-4 transition-opacity';
        modal.innerHTML = `
            <div class="bg-surface border border-outline-variant/30 rounded-xl max-w-md w-full overflow-hidden industrial-shadow flex flex-col">
                <div class="p-md border-b border-outline-variant/30 flex justify-between items-center">
                    <h3 class="font-headline-md text-primary flex items-center gap-2">
                        <span class="material-symbols-outlined">hub</span> Connect Device
                    </h3>
                    <button id="close-modal" class="text-outline hover:text-white transition-colors">
                        <span class="material-symbols-outlined">close</span>
                    </button>
                </div>
                
                <div class="flex border-b border-outline-variant/30">
                    <button id="tab-bt" class="flex-1 py-3 text-center font-label-caps text-primary border-b-2 border-primary bg-primary/5">BLUETOOTH</button>
                    <button id="tab-wifi" class="flex-1 py-3 text-center font-label-caps text-outline hover:bg-surface-variant transition-colors">WI-FI (TCP)</button>
                </div>

                <div id="bt-content" class="max-h-64 overflow-y-auto p-2"></div>
                
                <div id="wifi-content" class="hidden p-4 space-y-4">
                    <div>
                        <label class="block font-label-caps text-outline mb-1">IP ADDRESS</label>
                        <input id="wifi-ip" type="text" value="192.168.4.1" class="w-full bg-[#0F0F0F] border border-outline-variant/30 rounded p-2 text-primary font-data-sm focus:ring-1 focus:ring-primary outline-none">
                    </div>
                    <div>
                        <label class="block font-label-caps text-outline mb-1">PORT</label>
                        <input id="wifi-port" type="number" value="8080" class="w-full bg-[#0F0F0F] border border-outline-variant/30 rounded p-2 text-primary font-data-sm focus:ring-1 focus:ring-primary outline-none">
                    </div>
                    <button id="btn-connect-wifi" class="w-full bg-primary-container text-on-primary-container py-3 rounded-lg font-label-caps hover:brightness-110 active:scale-95 transition-all">
                        CONNECT VIA WI-FI
                    </button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        const tabBt = document.getElementById('tab-bt');
        const tabWifi = document.getElementById('tab-wifi');
        const btContent = document.getElementById('bt-content');
        const wifiContent = document.getElementById('wifi-content');

        tabBt.onclick = () => {
            tabBt.className = "flex-1 py-3 text-center font-label-caps text-primary border-b-2 border-primary bg-primary/5";
            tabWifi.className = "flex-1 py-3 text-center font-label-caps text-outline hover:bg-surface-variant transition-colors";
            btContent.classList.remove('hidden');
            wifiContent.classList.add('hidden');
            loadBluetoothDevices();
        };

        tabWifi.onclick = () => {
            tabWifi.className = "flex-1 py-3 text-center font-label-caps text-primary border-b-2 border-primary bg-primary/5";
            tabBt.className = "flex-1 py-3 text-center font-label-caps text-outline hover:bg-surface-variant transition-colors";
            wifiContent.classList.remove('hidden');
            btContent.classList.add('hidden');
        };

        document.getElementById('close-modal').onclick = () => modal.classList.add('hidden');

        document.getElementById('btn-connect-wifi').onclick = () => {
            const ip = document.getElementById('wifi-ip').value;
            const port = parseInt(document.getElementById('wifi-port').value);
            if (window.Android) window.Android.connectToWifi(ip, port);
            modal.classList.add('hidden');
        };

        function loadBluetoothDevices() {
            btContent.innerHTML = '<div class="text-center p-4 text-outline">Loading devices...</div>';
            if (window.Android) {
                try {
                    const devices = JSON.parse(window.Android.getPairedDevices());
                    if (devices.length === 0) {
                        btContent.innerHTML = '<div class="text-center p-4 text-outline">No paired devices found.</div>';
                    } else {
                        btContent.innerHTML = '';
                        devices.forEach(dev => {
                            const btn = document.createElement('button');
                            btn.className = 'w-full text-left p-sm hover:bg-surface-variant rounded-lg transition-colors flex items-center gap-md border-b border-outline-variant/10 last:border-0';
                            btn.innerHTML = `
                                <div class="bg-primary/10 p-2 rounded-full text-primary">
                                    <span class="material-symbols-outlined">bluetooth_connected</span>
                                </div>
                                <div>
                                    <div class="text-on-surface font-body-md font-semibold">${dev.name}</div>
                                    <div class="text-outline font-label-caps text-[10px]">${dev.address}</div>
                                </div>
                            `;
                            btn.onclick = () => {
                                window.Android.connectToDevice(dev.address);
                                modal.classList.add('hidden');
                            };
                            btContent.appendChild(btn);
                        });
                    }
                } catch (e) {
                    btContent.innerHTML = '<div class="text-center p-4 text-error">Error loading devices.</div>';
                }
            } else {
                btContent.innerHTML = '<div class="text-center p-4 text-outline">Android bridge not found.</div>';
            }
        }

        sensorsIcon.onclick = () => {
            modal.classList.remove('hidden');
            if (!btContent.classList.contains('hidden')) {
                loadBluetoothDevices();
            }
        };
    }
    
    // 3. Motion Control Specific logic (if on motion_control_1.html)
    const startBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('START'));
    if (startBtn) {
        // Clear out old injected logic
        let currentDirection = "CW";
        const inputs = document.querySelectorAll('input');
        const stepsInput = inputs[0]; 
        const speedInput = inputs[1]; 
        
        const originalToggleDir = window.toggleDir;
        if(originalToggleDir) {
            window.toggleDir = function(btn) {
                originalToggleDir(btn);
                currentDirection = btn.innerText.includes("CW") && !btn.innerText.includes("CCW") ? "CW" : "CCW";
            };
        }

        const buttons = Array.from(document.querySelectorAll('button'));
        const stopBtn = buttons.find(b => b.innerText.includes('STOP') && !b.innerText.includes('EMERGENCY'));
        const pauseBtn = buttons.find(b => b.innerText.includes('PAUSE'));
        const resumeBtn = buttons.find(b => b.innerText.includes('RESUME'));
        const homeBtn = buttons.find(b => b.innerText.includes('HOME') && !b.innerText.includes('EMERGENCY'));
        const eStopBtn = document.getElementById('e-stop');

        // We clone and replace buttons to remove previous event listeners injected directly into HTML
        function attachNewListener(btn, callback) {
            if(!btn) return;
            const newBtn = btn.cloneNode(true);
            btn.parentNode.replaceChild(newBtn, btn);
            newBtn.addEventListener('click', callback);
        }

        attachNewListener(startBtn, () => {
            if (window.Android) window.Android.startMotion(parseInt(stepsInput.value), parseInt(speedInput.value), currentDirection);
        });
        attachNewListener(stopBtn, () => {
            if (window.Android) window.Android.stopMotion();
        });
        attachNewListener(pauseBtn, () => {
            if (window.Android) window.Android.pauseMotion();
        });
        attachNewListener(resumeBtn, () => {
            if (window.Android) window.Android.resumeMotion();
        });
        attachNewListener(homeBtn, () => {
            if (window.Android) window.Android.homeMotion();
        });
        attachNewListener(eStopBtn, () => {
            if (window.Android) window.Android.emergencyStop();
            // Reattach active states for CSS animation manually since we cloned it
            eStopBtn.classList.toggle('e-stop-active');
            eStopBtn.classList.toggle('e-stop-heartbeat');
        });
    }
    
    // Stop the fake simulation setInterval if it exists (by overriding or clearing timeouts)
    // A bit hacky, but we can clear all intervals up to 1000
    for(let i=0; i<100; i++) clearInterval(i);
});
