function Header({ bridge, model }) {
  return (
    <header className="header">
      <div className="brand">
        <div className="brand-mark">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M2 18 Q6 8 12 8 Q18 8 22 18" stroke="#4fd8ff" strokeWidth="1.6" fill="none" />
            <path d="M12 8 V4 M6 18 V11 M18 18 V11" stroke="#4fd8ff" strokeWidth="1.4" />
            <path d="M2 18 H22" stroke="#4fd8ff" strokeWidth="1.6" />
          </svg>
        </div>
        <div>
          <div className="brand-title">BridgeSense</div>
          <div className="brand-sub">{bridge.name.toUpperCase()} · {bridge.sector.toUpperCase()}</div>
        </div>
      </div>

      <div className="header-right">
        <div className="uptime-pill">
          <span className="status-dot good pulse" />
          SYSTEM LIVE · {bridge.uptime_pct}% UPTIME
        </div>
        <div className="npu-chip">
          NPU: {model.target.split('(')[1]?.replace(')', '') || model.target}
        </div>
      </div>
    </header>
  );
}

export default Header;
