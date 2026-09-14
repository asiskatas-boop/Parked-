import { useState, useEffect, useRef, useCallback } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

type Screen = 'bluetooth' | 'permissions' | 'connected' | 'parked' | 'nearby' | 'empty' | 'adjusting' | 'settings'

const DEVICES = ['BMW 320i', 'VW MEDIA', 'Golf Bluetooth', 'Ford Audio', 'MyCar']

const CAR_COORDS: [number, number] = [40.75801, -73.98553]
const USER_COORDS: [number, number] = [40.75693, -73.98422]
const CAR_ADDRESS = 'W 47th St near Broadway'
const GPS_ACCURACY_M = 8

const LIGHT_TILES = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png'
const DARK_TILES  = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
const TILE_ATTR   = '© <a href="https://www.openstreetmap.org/copyright">OSM</a> © <a href="https://carto.com/">CARTO</a>'

// ── Color tokens ──────────────────────────────────────────────────────────────
const C = {
  // Primary blue
  blueL:  '#2563EB',
  blueD:  '#4F8EF7',
  // Gradients
  btnGradL: 'linear-gradient(145deg, #3B82F6 0%, #1D4ED8 100%)',
  btnGradD: 'linear-gradient(145deg, #60A5FA 0%, #3B82F6 100%)',
  // Backgrounds
  bgL:     '#F5F4F0',
  bgD:     '#0C1220',
  // Panel/card surfaces
  panelL:  '#FFFFFF',
  panelD:  '#192035',
  cardD:   '#1E2A40',
  // Text
  textL:   '#0F172A',
  textD:   '#F1F5F9',
  // Muted text
  mutedL:  '#64748B',
  mutedD:  '#64748B',
  // Borders
  borderL: 'rgba(0,0,0,0.07)',
  borderD: 'rgba(255,255,255,0.08)',
  // Accent tints
  blueTintL: 'rgba(37,99,235,0.07)',
  blueTintD: 'rgba(79,142,247,0.14)',
}

const CAR_SVG = `<svg viewBox="0 0 24 24" fill="white" width="22" height="22"><path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z"/></svg>`

function makeCarIcon(dark: boolean, pulse: boolean) {
  const bg    = dark ? 'linear-gradient(145deg, #60A5FA, #2563EB)' : 'linear-gradient(145deg, #3B82F6, #1D4ED8)'
  const glow  = dark ? '0 4px 20px rgba(96,165,250,0.6), 0 1px 3px rgba(0,0,0,0.5)' : '0 4px 20px rgba(37,99,235,0.5), 0 1px 4px rgba(0,0,0,0.25)'
  const ringColor = dark ? '#60A5FA' : '#3B82F6'
  const pulseRing = pulse ? `
    <div style="position:absolute;top:-12px;left:-12px;width:68px;height:68px;border-radius:50%;background:${ringColor};opacity:0.2;animation:pulse-ring 1.5s cubic-bezier(0.2,0.6,0.4,1) infinite;"></div>
    <div style="position:absolute;top:-12px;left:-12px;width:68px;height:68px;border-radius:50%;background:${ringColor};opacity:0.12;animation:pulse-ring 1.5s cubic-bezier(0.2,0.6,0.4,1) 0.5s infinite;"></div>
    <div style="position:absolute;top:-12px;left:-12px;width:68px;height:68px;border-radius:50%;background:${ringColor};opacity:0.07;animation:pulse-ring 1.5s cubic-bezier(0.2,0.6,0.4,1) 1s infinite;"></div>` : ''
  return L.divIcon({
    className: '',
    iconSize: [44, 60],
    iconAnchor: [22, 60],
    html: `<div style="position:relative;display:flex;flex-direction:column;align-items:center;">
      ${pulseRing}
      <div style="width:44px;height:44px;border-radius:50%;background:${bg};display:flex;align-items:center;justify-content:center;box-shadow:${glow};position:relative;">
        ${CAR_SVG}
      </div>
      <div style="width:0;height:0;border-left:7px solid transparent;border-right:7px solid transparent;border-top:11px solid ${dark ? '#3B82F6' : '#1D4ED8'};margin-top:-1px;filter:drop-shadow(0 3px 3px rgba(0,0,0,0.22));"></div>
    </div>`,
  })
}

function makeUserIcon(dark: boolean) {
  const blue = dark ? '#60A5FA' : '#2563EB'
  return L.divIcon({
    className: '',
    iconSize: [22, 22],
    iconAnchor: [11, 11],
    html: `<div style="position:relative;width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
      <div style="position:absolute;width:22px;height:22px;border-radius:50%;background:${blue};opacity:0.25;animation:ping 2.2s cubic-bezier(0,0,0.2,1) infinite;"></div>
      <div style="width:14px;height:14px;border-radius:50%;background:${blue};border:2.5px solid white;box-shadow:0 2px 10px ${blue}80;"></div>
    </div>`,
  })
}

// ── Leaflet map ───────────────────────────────────────────────────────────────

function LeafletMap({ dark, screen, carCoords, pulse, onMapRef }: {
  dark: boolean; screen: Screen; carCoords: [number, number]; pulse: boolean; onMapRef: (m: L.Map | null) => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef       = useRef<L.Map | null>(null)
  const tileRef      = useRef<L.TileLayer | null>(null)
  const carMarkerRef = useRef<L.Marker | null>(null)
  const userMarkerRef= useRef<L.Marker | null>(null)

  useEffect(() => {
    if (!containerRef.current) return
    const map = L.map(containerRef.current, {
      center: carCoords, zoom: 17,
      zoomControl: false, attributionControl: true,
      dragging: false, scrollWheelZoom: false,
      doubleClickZoom: false, touchZoom: false, keyboard: false,
    })
    map.attributionControl.setPrefix('')
    mapRef.current = map
    onMapRef(map)
    tileRef.current = L.tileLayer(dark ? DARK_TILES : LIGHT_TILES, { attribution: TILE_ATTR, maxZoom: 19 }).addTo(map)
    return () => { map.remove(); mapRef.current = null; onMapRef(null) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const map = mapRef.current; if (!map) return
    tileRef.current?.remove()
    tileRef.current = L.tileLayer(dark ? DARK_TILES : LIGHT_TILES, { attribution: TILE_ATTR, maxZoom: 19 }).addTo(map)
  }, [dark])

  useEffect(() => {
    const map = mapRef.current; if (!map) return
    carMarkerRef.current?.remove(); carMarkerRef.current = null
    userMarkerRef.current?.remove(); userMarkerRef.current = null

    const showCar  = ['connected', 'parked', 'nearby'].includes(screen)
    const showUser = ['connected', 'parked', 'nearby', 'adjusting'].includes(screen)
    if (showCar)  carMarkerRef.current  = L.marker(carCoords,   { icon: makeCarIcon(dark, pulse) }).addTo(map)
    if (showUser) userMarkerRef.current = L.marker(USER_COORDS, { icon: makeUserIcon(dark) }).addTo(map)

    if (screen === 'adjusting') { map.dragging.enable(); map.touchZoom.enable(); map.setView(carCoords, 17, { animate: true }) }
    else { map.dragging.disable(); map.touchZoom.disable() }

    if (screen === 'connected' || screen === 'parked') map.setView(carCoords, 17, { animate: true })
    if (screen === 'nearby') map.setView(USER_COORDS, 18, { animate: true })
    if (screen === 'empty')  map.setView(carCoords, 15, { animate: true })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screen, dark, pulse])

  return <div ref={containerRef} className="absolute inset-0" style={{ zIndex: 0 }} />
}

// ── Helpers ───────────────────────────────────────────────────────────────────

async function sendParkingNotification(device: string) {
  if (!('Notification' in window)) return
  let perm = Notification.permission
  if (perm === 'default') perm = await Notification.requestPermission()
  if (perm !== 'granted') return
  new Notification('Car parked', {
    body: `${device} disconnected. Saved at ${CAR_ADDRESS}.`,
    tag: 'car-parked',
  })
}

function mapsDirectionsUrl(coords: [number, number]) {
  return `https://www.google.com/maps/dir/?api=1&destination=${coords[0]},${coords[1]}&travelmode=walking`
}

// Animated count-up number
function CountUp({ to, dark }: { to: number; dark: boolean }) {
  const [val, setVal] = useState(0)
  useEffect(() => {
    setVal(0)
    const start = performance.now()
    const dur = 900
    function step(now: number) {
      const t = Math.min((now - start) / dur, 1)
      const eased = 1 - Math.pow(1 - t, 3)
      setVal(Math.round(eased * to))
      if (t < 1) requestAnimationFrame(step)
    }
    requestAnimationFrame(step)
  }, [to])
  return (
    <span style={{ color: dark ? C.blueD : C.blueL }}>
      {val}
    </span>
  )
}

// ── Icons ─────────────────────────────────────────────────────────────────────

function IconBluetooth({ size = 16, className = '' }: { size?: number; className?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" className={className}><polyline points="6.5 6.5 17.5 17.5 12 23 12 1 17.5 6.5 6.5 17.5" /></svg>
}
function IconChevronRight({ size = 16 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><path d="M9 18l6-6-6-6" /></svg>
}
function IconNavigation({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z" /></svg>
}
function IconMapPin({ size = 16 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" /><circle cx="12" cy="10" r="3" /></svg>
}
function IconMoreVertical({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="1.5" /><circle cx="12" cy="12" r="1.5" /><circle cx="12" cy="19" r="1.5" /></svg>
}
function IconX({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
}
function IconSun({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="5" /><line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" /><line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" /><line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" /><line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" /></svg>
}
function IconMoon({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" /></svg>
}
function IconSettings({ size = 18 }: { size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
}

// ── Primary gradient button ───────────────────────────────────────────────────

function PrimaryBtn({ dark, onClick, href, children, className = '' }: {
  dark: boolean; onClick?: () => void; href?: string; children: React.ReactNode; className?: string
}) {
  const style = {
    padding: '15px', fontSize: 16,
    background: dark ? C.btnGradD : C.btnGradL,
    color: '#FFFFFF',
    boxShadow: dark
      ? '0 4px 20px rgba(79,142,247,0.45), 0 1px 3px rgba(0,0,0,0.3)'
      : '0 4px 20px rgba(37,99,235,0.4),  0 1px 3px rgba(0,0,0,0.15)',
    position: 'relative' as const, overflow: 'hidden',
  }
  const inner = (
    <>
      <div className="shimmer-sweep" />
      {children}
    </>
  )
  if (href) return (
    <a href={href} target="_blank" rel="noopener noreferrer" className={`btn-press w-full rounded-2xl font-semibold flex items-center justify-center gap-2 no-underline ${className}`} style={{ ...style, display: 'flex' }}>
      {inner}
    </a>
  )
  return (
    <button onClick={onClick} className={`btn-press w-full rounded-2xl font-semibold flex items-center justify-center gap-2 ${className}`} style={{ ...style, display: 'flex' }}>
      {inner}
    </button>
  )
}

// ── Setup screens ─────────────────────────────────────────────────────────────

function SetupBluetooth({ dark, onSelect }: { dark: boolean; onSelect: (d: string) => void }) {
  const [selected, setSelected] = useState<string | null>(null)
  const [tapped, setTapped]     = useState<string | null>(null)

  function tap(device: string) {
    setTapped(device)
    setSelected(device)
    setTimeout(() => setTapped(null), 300)
  }

  return (
    <div className="absolute inset-0 flex flex-col" style={{ background: dark ? C.bgD : C.bgL }}>
      <div className="flex-1 flex flex-col justify-center px-6 pb-8 pt-20">
        <div className="flex items-center justify-center rounded-2xl mb-8 self-start"
          style={{ width: 56, height: 56, background: dark ? 'rgba(79,142,247,0.18)' : 'rgba(37,99,235,0.1)' }}>
          <IconBluetooth size={24} className={dark ? 'text-blue-400' : 'text-blue-600'} />
        </div>
        <h1 className="font-semibold mb-1.5" style={{ fontSize: 26, lineHeight: 1.2, color: dark ? C.textD : C.textL, letterSpacing: '-0.02em' }}>
          Choose your car
        </h1>
        <p className="mb-8" style={{ fontSize: 15, color: dark ? C.mutedD : C.mutedL, lineHeight: 1.5 }}>
          Select the Bluetooth connection your car uses.
        </p>
        <div className="rounded-2xl overflow-hidden"
          style={{ background: dark ? C.cardD : C.panelL, border: `1px solid ${dark ? C.borderD : C.borderL}`, boxShadow: dark ? '0 2px 12px rgba(0,0,0,0.3)' : '0 2px 12px rgba(0,0,0,0.06)' }}>
          {DEVICES.map((device, i) => (
            <button key={device} onClick={() => tap(device)}
              className="btn-press-sm w-full flex items-center gap-3 px-4 py-3.5 text-left transition-colors"
              style={{
                borderBottom: i < DEVICES.length - 1 ? `1px solid ${dark ? C.borderD : C.borderL}` : 'none',
                background: tapped === device
                  ? (dark ? 'rgba(79,142,247,0.25)' : 'rgba(37,99,235,0.12)')
                  : selected === device
                    ? (dark ? C.blueTintD : C.blueTintL)
                    : 'transparent',
                transition: 'background 0.2s ease',
              }}>
              <IconBluetooth size={16} className={selected === device ? (dark ? 'text-blue-400' : 'text-blue-600') : (dark ? 'text-slate-500' : 'text-slate-400')} />
              <span className="flex-1 font-medium" style={{ fontSize: 15, color: dark ? C.textD : C.textL }}>{device}</span>
              <div
                className="rounded-full flex items-center justify-center transition-all"
                style={{
                  width: 20, height: 20,
                  background: selected === device ? (dark ? C.blueD : C.blueL) : 'transparent',
                  border: selected === device ? 'none' : `2px solid ${dark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.12)'}`,
                  transform: selected === device ? 'scale(1)' : 'scale(0.85)',
                  transition: 'all 0.2s cubic-bezier(0.34,1.56,0.64,1)',
                }}>
                {selected === device && (
                  <svg viewBox="0 0 24 24" fill="white" style={{ width: 11, height: 11 }}><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" /></svg>
                )}
              </div>
            </button>
          ))}
        </div>
      </div>
      <div className="px-6 pb-10">
        <PrimaryBtn dark={dark} onClick={() => selected && onSelect(selected)}>
          Continue
        </PrimaryBtn>
      </div>
    </div>
  )
}

function PermissionScreen({ dark, device, onAllow }: { dark: boolean; device: string; onAllow: () => void }) {
  return (
    <div className="absolute inset-0 flex flex-col" style={{ background: dark ? C.bgD : C.bgL }}>
      <div className="flex-1 flex flex-col justify-center px-6">
        <div className="flex items-center justify-center rounded-2xl mb-8 self-start"
          style={{ width: 56, height: 56, background: dark ? 'rgba(79,142,247,0.18)' : 'rgba(37,99,235,0.1)' }}>
          <IconMapPin size={24} className={dark ? 'text-blue-400' : 'text-blue-600'} />
        </div>
        <h1 className="font-semibold mb-2" style={{ fontSize: 26, lineHeight: 1.2, color: dark ? C.textD : C.textL, letterSpacing: '-0.02em' }}>
          Allow location access
        </h1>
        <p className="mb-6" style={{ fontSize: 15, color: dark ? C.mutedD : C.mutedL, lineHeight: 1.6 }}>
          We use your location when{' '}
          <span style={{ color: dark ? '#CBD5E1' : '#1E293B', fontWeight: 500 }}>{device}</span>{' '}
          disconnects to remember where you parked.
        </p>
        <div className="rounded-2xl p-4 mb-8"
          style={{ background: dark ? C.cardD : C.panelL, border: `1px solid ${dark ? C.borderD : C.borderL}` }}>
          {[
            { label: 'When disconnected', desc: 'Saves location on Bluetooth disconnect' },
            { label: 'Precise location',  desc: 'Required for accurate parking spot' },
            { label: 'Never in background', desc: 'Only triggered by Bluetooth events' },
          ].map(({ label, desc }) => (
            <div key={label} className="flex gap-3 py-2.5 items-start">
              <div className="rounded-full flex-shrink-0" style={{ width: 6, height: 6, marginTop: 7, background: dark ? C.blueD : C.blueL }} />
              <div>
                <div style={{ fontSize: 14, fontWeight: 500, color: dark ? C.textD : C.textL }}>{label}</div>
                <div style={{ fontSize: 13, color: dark ? C.mutedD : C.mutedL }}>{desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="px-6 pb-10 flex flex-col gap-3">
        <PrimaryBtn dark={dark} onClick={onAllow}>Allow Location</PrimaryBtn>
        <button className="btn-press w-full rounded-2xl font-medium" style={{ padding: '14px', fontSize: 15, color: dark ? C.mutedD : C.mutedL }}>
          Not now
        </button>
      </div>
    </div>
  )
}

// ── Settings ──────────────────────────────────────────────────────────────────

function SettingsPanel({ dark, device, onClose, onChangeState }: {
  dark: boolean; device: string; onClose: () => void; onChangeState: (s: Screen) => void
}) {
  const [autoDetect, setAutoDetect] = useState(true)
  const [navApp, setNavApp] = useState<'default' | 'apple' | 'google'>('google')

  const divider = `1px solid ${dark ? C.borderD : C.borderL}`
  const card = { background: dark ? C.cardD : C.panelL, border: `1px solid ${dark ? C.borderD : C.borderL}` }

  const sectionLabel = (text: string) => (
    <div className="px-4 pb-1.5 pt-5" style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase' as const, color: dark ? '#475569' : '#94A3B8' }}>
      {text}
    </div>
  )

  return (
    <div className="absolute inset-0 flex flex-col" style={{ background: dark ? C.bgD : C.bgL }}>
      <div className="flex items-center justify-between px-5 pt-14 pb-4" style={{ borderBottom: divider }}>
        <span style={{ fontSize: 18, fontWeight: 600, color: dark ? C.textD : C.textL, letterSpacing: '-0.01em' }}>Settings</span>
        <button onClick={onClose} className="btn-press-sm rounded-full p-2" style={{ color: dark ? C.mutedD : C.mutedL }}>
          <IconX size={20} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sectionLabel('Car Bluetooth')}
        <div className="mx-4 rounded-2xl overflow-hidden" style={card}>
          <button className="btn-press-sm w-full flex items-center justify-between px-4 py-3.5" onClick={() => onChangeState('bluetooth')}>
            <span style={{ fontSize: 15, color: dark ? C.textD : C.textL }}>Selected device</span>
            <span className="flex items-center gap-1.5">
              <span style={{ fontSize: 14, fontWeight: 500, color: dark ? C.blueD : C.blueL }}>{device}</span>
              <span style={{ color: dark ? '#475569' : '#94A3B8' }}><IconChevronRight size={14} /></span>
            </span>
          </button>
        </div>

        {sectionLabel('Location')}
        <div className="mx-4 rounded-2xl overflow-hidden" style={card}>
          <div className="flex items-center justify-between px-4 py-3.5">
            <span style={{ fontSize: 15, color: dark ? C.textD : C.textL }}>Automatic detection</span>
            <button onClick={() => setAutoDetect(v => !v)}
              className="rounded-full flex items-center"
              style={{
                width: 48, height: 28,
                background: autoDetect ? (dark ? C.blueD : C.blueL) : (dark ? '#334155' : '#CBD5E1'),
                padding: 3,
                justifyContent: autoDetect ? 'flex-end' : 'flex-start',
                transition: 'background 0.25s ease, justify-content 0s',
                boxShadow: autoDetect ? (dark ? '0 0 12px rgba(79,142,247,0.4)' : '0 0 12px rgba(37,99,235,0.3)') : 'none',
              }}>
              <div className="rounded-full bg-white"
                style={{ width: 22, height: 22, boxShadow: '0 1px 4px rgba(0,0,0,0.25)', transition: 'transform 0.25s cubic-bezier(0.34,1.56,0.64,1)' }} />
            </button>
          </div>
        </div>

        {sectionLabel('Navigation app')}
        <div className="mx-4 rounded-2xl overflow-hidden" style={card}>
          {([
            { id: 'default', label: 'System default', sub: 'Uses your device default' },
            { id: 'apple',   label: 'Apple Maps',     sub: 'iPhone & iPad' },
            { id: 'google',  label: 'Google Maps',    sub: 'Requires app installed' },
          ] as const).map(({ id, label, sub }, i, arr) => (
            <button key={id} className="btn-press-sm w-full flex items-center justify-between px-4 py-3 text-left"
              onClick={() => setNavApp(id)}
              style={{
                borderBottom: i < arr.length - 1 ? divider : 'none',
                background: navApp === id ? (dark ? C.blueTintD : C.blueTintL) : 'transparent',
                transition: 'background 0.15s ease',
              }}>
              <div>
                <div style={{ fontSize: 15, color: dark ? C.textD : C.textL, fontWeight: navApp === id ? 500 : 400 }}>{label}</div>
                <div style={{ fontSize: 12, color: dark ? '#475569' : '#94A3B8', marginTop: 1 }}>{sub}</div>
              </div>
              <div style={{ width: 22, height: 22, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {navApp === id && (
                  <svg viewBox="0 0 24 24" fill={dark ? C.blueD : C.blueL} style={{ width: 20, height: 20 }} className="animate-slide-right">
                    <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" />
                  </svg>
                )}
              </div>
            </button>
          ))}
        </div>

        {sectionLabel('Parking location')}
        <div className="mx-4 rounded-2xl overflow-hidden" style={card}>
          <a href={mapsDirectionsUrl(CAR_COORDS)} target="_blank" rel="noopener noreferrer"
            className="btn-press-sm w-full flex items-center justify-between px-4 py-3.5 gap-3">
            <div className="flex flex-col items-start min-w-0">
              <span style={{ fontSize: 15, color: dark ? C.textD : C.textL, fontWeight: 500 }}>{CAR_ADDRESS}</span>
              <span style={{ fontSize: 12, color: dark ? '#475569' : '#94A3B8', marginTop: 1 }}>
                {CAR_COORDS[0].toFixed(5)}, {CAR_COORDS[1].toFixed(5)}
              </span>
            </div>
            <span className="flex items-center gap-0.5 flex-shrink-0" style={{ color: dark ? C.blueD : C.blueL }}>
              <span style={{ fontSize: 13, fontWeight: 500 }}>Open</span>
              <IconChevronRight size={13} />
            </span>
          </a>
        </div>

        {sectionLabel('About')}
        <div className="mx-4 rounded-2xl overflow-hidden" style={card}>
          <button className="btn-press-sm w-full flex items-center justify-between px-4 py-3.5" style={{ borderBottom: divider }}>
            <span style={{ fontSize: 15, color: dark ? C.textD : C.textL }}>Privacy</span>
          </button>
          <div className="flex items-center justify-between px-4 py-3.5">
            <span style={{ fontSize: 15, color: dark ? C.textD : C.textL }}>App version</span>
            <span style={{ fontSize: 14, color: dark ? '#475569' : '#94A3B8' }}>1.0.0</span>
          </div>
        </div>
        <div className="h-10" />
      </div>
    </div>
  )
}

// ── Overflow menu ─────────────────────────────────────────────────────────────

function OverflowMenu({ dark, onForget, onChangeCar, onSettings, onClose }: {
  dark: boolean; onForget: () => void; onChangeCar: () => void; onSettings: () => void; onClose: () => void
}) {
  const items = [
    { label: 'Forget location',       action: onForget,   danger: true },
    { label: 'Change car Bluetooth',  action: onChangeCar },
    { label: 'Settings',              action: onSettings },
  ]
  const divider = `1px solid ${dark ? C.borderD : C.borderL}`
  return (
    <>
      <div className="absolute inset-0" style={{ zIndex: 40 }} onClick={onClose} />
      <div className="absolute right-4 rounded-2xl overflow-hidden animate-spring-up"
        style={{ bottom: 230, minWidth: 215, background: dark ? C.cardD : C.panelL, boxShadow: dark ? '0 12px 32px rgba(0,0,0,0.6)' : '0 12px 32px rgba(0,0,0,0.16)', border: `1px solid ${dark ? C.borderD : C.borderL}`, zIndex: 45 }}>
        {items.map(({ label, action, danger }, i) => (
          <button key={label} className="btn-press-sm w-full px-4 py-3.5 text-left"
            style={{ fontSize: 15, fontWeight: 500, color: danger ? '#EF4444' : (dark ? C.textD : C.textL), borderBottom: i < items.length - 1 ? divider : 'none' }}
            onClick={() => { action(); onClose() }}>
            {label}
          </button>
        ))}
      </div>
    </>
  )
}

// ── Map controls ──────────────────────────────────────────────────────────────

function MapControlBtn({ dark, onClick, children, label, showLabel }: {
  dark: boolean; onClick: () => void; children: React.ReactNode; label: string; showLabel?: boolean
}) {
  return (
    <button onClick={onClick} aria-label={label}
      className="btn-press flex items-center gap-2 rounded-2xl"
      style={{
        height: 44,
        paddingLeft: showLabel ? 12 : 0,
        paddingRight: showLabel ? 14 : 0,
        width: showLabel ? 'auto' : 44,
        justifyContent: 'center',
        background: dark ? 'rgba(25,32,53,0.92)' : 'rgba(255,255,255,0.92)',
        backdropFilter: 'blur(12px)',
        boxShadow: dark ? '0 2px 12px rgba(0,0,0,0.5)' : '0 2px 12px rgba(0,0,0,0.12)',
        border: `1px solid ${dark ? C.borderD : C.borderL}`,
        minWidth: 44,
        transition: 'width 0.3s cubic-bezier(0.34,1.56,0.64,1), padding 0.3s ease',
      }}>
      <span className="flex-shrink-0">{children}</span>
      {showLabel && (
        <span className="animate-fade-in-up" style={{ fontSize: 13, fontWeight: 500, color: dark ? '#CBD5E1' : '#374151', whiteSpace: 'nowrap' }}>
          {label}
        </span>
      )}
    </button>
  )
}

// ── Bottom panel ──────────────────────────────────────────────────────────────

function BottomPanel({ dark, children }: { dark: boolean; children: React.ReactNode }) {
  return (
    <div className="absolute bottom-0 left-0 right-0 animate-spring-up" style={{ padding: '0 0 34px 0', zIndex: 20 }}>
      <div className="mx-3 rounded-3xl px-5 py-5"
        style={{
          background: dark ? C.panelD : C.panelL,
          boxShadow: dark
            ? '0 -4px 40px rgba(0,0,0,0.5), 0 8px 20px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.06)'
            : '0 -4px 40px rgba(0,0,0,0.07), 0 8px 20px rgba(0,0,0,0.05)',
          border: `1px solid ${dark ? C.borderD : C.borderL}`,
        }}>
        {children}
      </div>
    </div>
  )
}

// ── Parked banner ─────────────────────────────────────────────────────────────

function ParkedBanner({ dark, onDismiss, poorAccuracy }: { dark: boolean; onDismiss: () => void; poorAccuracy?: boolean }) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 5000)
    return () => clearTimeout(t)
  }, [onDismiss])

  return (
    <div className="absolute top-0 left-0 right-0 flex justify-center animate-bounce-in" style={{ paddingTop: 52, zIndex: 30 }}>
      <div className="flex items-center gap-3 rounded-2xl px-4 py-3 mx-4"
        style={{
          background: dark ? C.cardD : C.panelL,
          boxShadow: dark ? '0 8px 32px rgba(0,0,0,0.6)' : '0 8px 32px rgba(0,0,0,0.14)',
          border: `1px solid ${dark ? C.borderD : C.borderL}`,
          maxWidth: 340,
        }}>
        <div className="rounded-full flex-shrink-0 flex items-center justify-center"
          style={{ width: 34, height: 34, background: dark ? 'rgba(79,142,247,0.18)' : 'rgba(37,99,235,0.09)' }}>
          <svg viewBox="0 0 24 24" fill={dark ? C.blueD : C.blueL} style={{ width: 18, height: 18 }}>
            <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z" />
          </svg>
        </div>
        <div className="flex-1 min-w-0">
          <div style={{ fontSize: 13, fontWeight: 600, color: dark ? C.textD : C.textL }}>Parking location saved</div>
          <div style={{ fontSize: 12, color: dark ? C.mutedD : C.mutedL, marginTop: 1 }}>
            {poorAccuracy ? `Approximate location · ±${GPS_ACCURACY_M * 5} m` : `${CAR_ADDRESS} · just now`}
          </div>
        </div>
        <button onClick={onDismiss} className="btn-press-sm" style={{ color: dark ? '#475569' : '#94A3B8', flexShrink: 0, padding: 4 }}>
          <IconX size={15} />
        </button>
      </div>
    </div>
  )
}

// ── Toast ─────────────────────────────────────────────────────────────────────

function Toast({ dark, message }: { dark: boolean; message: string }) {
  return (
    <div className="animate-toast fixed left-1/2 rounded-full px-5 py-2.5"
      style={{ bottom: 112, transform: 'translateX(-50%)', background: dark ? C.cardD : '#0F172A', color: '#FFFFFF', fontSize: 14, fontWeight: 500, boxShadow: '0 4px 20px rgba(0,0,0,0.35)', whiteSpace: 'nowrap', zIndex: 100, border: `1px solid ${dark ? C.borderD : 'transparent'}` }}>
      {message}
    </div>
  )
}

// ── App ───────────────────────────────────────────────────────────────────────

export default function App() {
  const [screen, setScreen]       = useState<Screen>('bluetooth')
  const [device, setDevice]       = useState<string>('BMW 320i')
  const [dark, setDark]           = useState(false)
  const [pulse, setPulse]         = useState(false)
  const [toast, setToastMsg]      = useState<string | null>(null)
  const [showBanner, setShowBanner] = useState(false)
  const [showOverflow, setShowOverflow] = useState(false)
  const [controlTaps, setControlTaps]   = useState(0)
  const mapInstanceRef = useRef<L.Map | null>(null)

  const showControlLabels = controlTaps < 3

  function showToast(msg: string) {
    setToastMsg(msg)
    setTimeout(() => setToastMsg(null), 2800)
  }

  function handleSelectDevice(d: string) { setDevice(d); setScreen('permissions') }
  function handleAllow() { setScreen('empty') }

  async function handleDisconnect() {
    setPulse(true)
    setShowBanner(true)
    setScreen('parked')
    setTimeout(() => setPulse(false), 3500)
    await sendParkingNotification(device)
  }

  function handleForget() { setScreen('empty'); showToast('Location forgotten') }

  function handleSetHere() {
    const center = mapInstanceRef.current?.getCenter()
    setPulse(true)
    setScreen('parked')
    showToast(center ? `Car set to ${center.lat.toFixed(4)}, ${center.lng.toFixed(4)}` : 'Car location updated')
    setTimeout(() => setPulse(false), 3500)
  }

  const recenterCar = useCallback(() => {
    mapInstanceRef.current?.setView(CAR_COORDS, 17, { animate: true })
    setControlTaps(t => t + 1)
  }, [])

  const recenterMe = useCallback(() => {
    mapInstanceRef.current?.setView(USER_COORDS, 17, { animate: true })
    setControlTaps(t => t + 1)
  }, [])

  const isMapScreen = ['connected', 'parked', 'nearby', 'empty', 'adjusting'].includes(screen)
  const mapsUrl = mapsDirectionsUrl(CAR_COORDS)

  // Shared top chip colors
  const chipBg     = dark ? 'rgba(25,32,53,0.92)'  : 'rgba(255,255,255,0.92)'
  const chipBorder = `1px solid ${dark ? C.borderD : C.borderL}`
  const chipShadow = dark ? '0 2px 12px rgba(0,0,0,0.5)' : '0 2px 12px rgba(0,0,0,0.1)'

  return (
    <div className="relative flex items-start justify-center min-h-full"
      style={{ background: dark ? '#070C18' : '#DFE0DC' }}>
      <div className="relative overflow-hidden"
        style={{ width: 390, height: '100dvh', maxHeight: 844, fontFamily: "'Inter', system-ui, -apple-system, sans-serif" }}>

        {isMapScreen && (
          <LeafletMap dark={dark} screen={screen} carCoords={CAR_COORDS} pulse={pulse} onMapRef={m => { mapInstanceRef.current = m }} />
        )}

        {screen === 'bluetooth'   && <SetupBluetooth dark={dark} onSelect={handleSelectDevice} />}
        {screen === 'permissions' && <PermissionScreen dark={dark} device={device} onAllow={handleAllow} />}
        {screen === 'settings'    && <SettingsPanel dark={dark} device={device} onClose={() => setScreen('parked')} onChangeState={setScreen} />}

        {/* BT status chip + dark toggle */}
        {isMapScreen && screen !== 'adjusting' && (
          <div className="absolute top-0 left-0 right-0 flex items-start justify-between px-4" style={{ paddingTop: 52, zIndex: 20 }}>
            <div className="flex items-center gap-1.5 rounded-full px-3 py-1.5"
              style={{ background: chipBg, backdropFilter: 'blur(12px)', boxShadow: chipShadow, border: chipBorder }}>
              <div
                className={screen === 'connected' ? 'animate-breathe' : ''}
                style={{
                  width: 7, height: 7, borderRadius: '50%',
                  background: screen === 'connected' ? '#22C55E' : (dark ? '#475569' : '#94A3B8'),
                  flexShrink: 0,
                }}
              />
              <span style={{ fontSize: 12, fontWeight: 500, color: dark ? '#CBD5E1' : '#334155' }}>
                {screen === 'connected' ? `Connected to ${device}` : `Watching: ${device}`}
              </span>
            </div>
            <button onClick={() => setDark(v => !v)}
              className="btn-press-sm flex items-center justify-center rounded-full"
              style={{ width: 36, height: 36, background: chipBg, backdropFilter: 'blur(12px)', boxShadow: chipShadow, color: dark ? '#94A3B8' : '#475569', border: chipBorder }}>
              {dark ? <IconSun size={16} /> : <IconMoon size={16} />}
            </button>
          </div>
        )}

        {/* Dark toggle on setup screens */}
        {!isMapScreen && screen !== 'settings' && (
          <button onClick={() => setDark(v => !v)}
            className="btn-press-sm absolute flex items-center justify-center rounded-full"
            style={{ top: 52, right: 20, width: 36, height: 36, background: dark ? C.cardD : C.panelL, color: dark ? '#64748B' : '#94A3B8', boxShadow: dark ? '0 2px 10px rgba(0,0,0,0.4)' : '0 2px 10px rgba(0,0,0,0.1)', border: `1px solid ${dark ? C.borderD : C.borderL}`, zIndex: 20 }}>
            {dark ? <IconSun size={16} /> : <IconMoon size={16} />}
          </button>
        )}

        {/* Map controls */}
        {['connected', 'parked', 'nearby'].includes(screen) && (
          <div className="absolute right-4 flex flex-col items-end gap-2" style={{ bottom: 230, zIndex: 20 }}>
            <MapControlBtn dark={dark} onClick={recenterMe} label="My location" showLabel={showControlLabels}>
              <div className="rounded-full flex-shrink-0"
                style={{ width: 12, height: 12, background: dark ? C.blueD : C.blueL, border: '2px solid white', boxShadow: `0 1px 5px ${dark ? C.blueD : C.blueL}80` }} />
            </MapControlBtn>
            <MapControlBtn dark={dark} onClick={recenterCar} label="Parked car" showLabel={showControlLabels}>
              <svg viewBox="0 0 24 24" fill={dark ? C.blueD : C.blueL} style={{ width: 18, height: 18, flexShrink: 0 }}>
                <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z" />
              </svg>
            </MapControlBtn>
          </div>
        )}

        {showBanner && screen === 'parked' && (
          <ParkedBanner dark={dark} onDismiss={() => setShowBanner(false)} poorAccuracy={GPS_ACCURACY_M > 20} />
        )}

        {/* ── CONNECTED ── */}
        {screen === 'connected' && (
          <BottomPanel dark={dark}>
            <div className="flex items-start gap-3 mb-5">
              <div
                className="animate-breathe flex-shrink-0"
                style={{ width: 10, height: 10, borderRadius: '50%', background: '#22C55E', marginTop: 6, flexShrink: 0 }}
              />
              <div className="flex-1">
                <div style={{ fontSize: 17, fontWeight: 600, color: dark ? C.textD : C.textL, letterSpacing: '-0.01em' }}>
                  Car connected
                </div>
                <div style={{ fontSize: 14, color: dark ? C.mutedD : C.mutedL, marginTop: 3, lineHeight: 1.55 }}>
                  We'll save your parking location automatically when Bluetooth disconnects.
                </div>
              </div>
            </div>
            <div style={{ height: 1, background: dark ? C.borderD : C.borderL, marginBottom: 14, marginLeft: -20, marginRight: -20 }} />
            <button onClick={handleDisconnect}
              className="btn-press w-full text-center rounded-2xl py-3 font-medium"
              style={{ fontSize: 14, color: dark ? '#64748B' : '#64748B', background: dark ? 'rgba(255,255,255,0.05)' : 'rgba(15,23,42,0.04)' }}>
              Save current location now
            </button>
          </BottomPanel>
        )}

        {/* ── PARKED ── */}
        {screen === 'parked' && (
          <>
            {showOverflow && (
              <OverflowMenu dark={dark} onForget={handleForget} onChangeCar={() => setScreen('bluetooth')} onSettings={() => setScreen('settings')} onClose={() => setShowOverflow(false)} />
            )}
            <BottomPanel dark={dark}>
              <div className="flex items-start justify-between">
                <div>
                  <div style={{ fontSize: 11, fontWeight: 700, color: dark ? '#475569' : '#94A3B8', letterSpacing: '0.07em', textTransform: 'uppercase' }}>
                    Your car
                  </div>
                  <div style={{ fontSize: 48, fontWeight: 700, letterSpacing: '-0.035em', lineHeight: 1.0, marginTop: 2 }}>
                    <CountUp to={380} dark={dark} />
                    <span style={{ fontSize: 22, fontWeight: 500, letterSpacing: '-0.01em', color: dark ? '#475569' : '#94A3B8' }}> m</span>
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 500, color: dark ? '#CBD5E1' : '#1E293B', marginTop: 3, lineHeight: 1.3 }}>
                    {CAR_ADDRESS}
                  </div>
                  <div style={{ fontSize: 13, color: dark ? '#475569' : '#94A3B8', marginTop: 2 }}>
                    Parked 47 min ago
                  </div>
                </div>
                <button onClick={() => setShowOverflow(v => !v)}
                  className="btn-press-sm rounded-full flex items-center justify-center"
                  style={{ width: 44, height: 44, color: dark ? '#475569' : '#94A3B8', marginRight: -8, marginTop: -4 }}>
                  <IconMoreVertical size={20} />
                </button>
              </div>
              <div className="mt-4">
                <PrimaryBtn dark={dark} href={mapsUrl}>
                  <IconNavigation size={16} />
                  Directions
                </PrimaryBtn>
              </div>
              <div className="flex items-center justify-between mt-3 px-1">
                <button onClick={() => setScreen('adjusting')}
                  className="btn-press-sm"
                  style={{ fontSize: 14, fontWeight: 500, color: dark ? C.blueD : C.blueL, padding: '6px 0', minHeight: 44, display: 'flex', alignItems: 'center' }}>
                  Update location
                </button>
                <button onClick={() => setScreen('nearby')}
                  style={{ fontSize: 13, color: dark ? '#475569' : '#94A3B8', padding: '6px 0', minHeight: 44, display: 'flex', alignItems: 'center' }}>
                  Demo: nearby →
                </button>
              </div>
            </BottomPanel>
          </>
        )}

        {/* ── NEARBY ── */}
        {screen === 'nearby' && (
          <BottomPanel dark={dark}>
            <div className="flex items-center gap-3 mb-4">
              <div className="rounded-2xl flex items-center justify-center flex-shrink-0"
                style={{ width: 50, height: 50, background: dark ? C.blueTintD : C.blueTintL }}>
                <svg viewBox="0 0 24 24" fill={dark ? C.blueD : C.blueL} style={{ width: 24, height: 24 }}>
                  <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"/>
                </svg>
              </div>
              <div>
                <div style={{ fontSize: 17, fontWeight: 600, color: dark ? C.textD : C.textL, letterSpacing: '-0.01em' }}>Your car is nearby</div>
                <div style={{ fontSize: 36, fontWeight: 700, color: dark ? C.blueD : C.blueL, letterSpacing: '-0.03em', lineHeight: 1.1 }}>35 m</div>
              </div>
            </div>
            <button onClick={() => setScreen('parked')} className="btn-press w-full text-center py-2.5 font-medium"
              style={{ fontSize: 14, color: dark ? '#475569' : '#94A3B8' }}>
              Back
            </button>
          </BottomPanel>
        )}

        {/* ── EMPTY ── */}
        {screen === 'empty' && (
          <BottomPanel dark={dark}>
            <div style={{ fontSize: 17, fontWeight: 600, color: dark ? C.textD : C.textL, marginBottom: 5, letterSpacing: '-0.01em' }}>
              No parked location yet
            </div>
            <div style={{ fontSize: 14, color: dark ? C.mutedD : C.mutedL, lineHeight: 1.6, marginBottom: 18 }}>
              Connect to your car once. We'll save where you parked whenever Bluetooth disconnects.
            </div>
            <PrimaryBtn dark={dark} onClick={() => setScreen('connected')}>Demo: connect car</PrimaryBtn>
            <div className="flex justify-end mt-3.5">
              <button onClick={() => setScreen('settings')}
                className="btn-press-sm flex items-center gap-1.5 font-medium"
                style={{ fontSize: 13, color: dark ? '#475569' : '#94A3B8', minHeight: 44, alignItems: 'center', display: 'flex' }}>
                <IconSettings size={14} />
                Settings
              </button>
            </div>
          </BottomPanel>
        )}

        {/* ── ADJUSTING ── */}
        {screen === 'adjusting' && (
          <>
            <div className="absolute inset-0 flex items-center justify-center" style={{ zIndex: 10, pointerEvents: 'none', paddingBottom: 120 }}>
              <div className="flex flex-col items-center">
                <div style={{ color: dark ? C.blueD : C.blueL, marginBottom: 2 }}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 22, height: 22 }}>
                    <circle cx="12" cy="12" r="10" />
                    <line x1="22" y1="12" x2="18" y2="12" /><line x1="6" y1="12" x2="2" y2="12" />
                    <line x1="12" y1="6" x2="12" y2="2" /><line x1="12" y1="22" x2="12" y2="18" />
                  </svg>
                </div>
                <div className="rounded-full flex items-center justify-center"
                  style={{ width: 50, height: 50, background: dark ? C.btnGradD : C.btnGradL, boxShadow: dark ? '0 6px 24px rgba(79,142,247,0.55)' : '0 6px 24px rgba(37,99,235,0.45)' }}>
                  <svg viewBox="0 0 24 24" fill="white" style={{ width: 23, height: 23 }}>
                    <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"/>
                  </svg>
                </div>
                <div style={{ width: 0, height: 0, borderLeft: '7px solid transparent', borderRight: '7px solid transparent', borderTop: `11px solid ${dark ? '#3B82F6' : '#1D4ED8'}`, marginTop: -1 }} />
              </div>
            </div>

            <div className="absolute top-0 left-0 right-0 flex items-center justify-between px-4" style={{ paddingTop: 52, zIndex: 20 }}>
              <div className="flex items-center gap-1.5 rounded-full px-3 py-2"
                style={{ background: chipBg, backdropFilter: 'blur(12px)', fontSize: 13, fontWeight: 500, color: dark ? '#CBD5E1' : '#334155', boxShadow: chipShadow, border: chipBorder }}>
                Move the map to set location
              </div>
              <button onClick={() => setScreen('parked')}
                className="btn-press-sm flex items-center justify-center rounded-full"
                style={{ width: 36, height: 36, background: chipBg, backdropFilter: 'blur(12px)', color: dark ? '#94A3B8' : '#475569', border: chipBorder }}>
                <IconX size={16} />
              </button>
            </div>

            <div className="absolute bottom-0 left-0 right-0" style={{ padding: '0 12px 34px', zIndex: 20 }}>
              <PrimaryBtn dark={dark} onClick={handleSetHere}>Set Car Here</PrimaryBtn>
            </div>
          </>
        )}

        {toast && <Toast dark={dark} message={toast} />}
      </div>
    </div>
  )
}
