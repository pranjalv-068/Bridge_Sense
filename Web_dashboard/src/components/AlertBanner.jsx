import { useState } from 'react';

function AlertBanner({ alert }) {
  const [visible, setVisible] = useState(true);
  if (!visible || !alert) return null;

  return (
    <div className="alert-banner">
      <div className="alert-left">
        <div className="alert-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M12 3 L22 20 H2 Z" stroke="currentColor" strokeWidth="1.6" />
            <path d="M12 10 V14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            <circle cx="12" cy="17" r="1" fill="currentColor" />
          </svg>
        </div>
        <div>
          <div className="alert-title">STRUCTURAL ANOMALY DETECTED</div>
          <div className="alert-body">
            <b>{alert.node_id}</b> · {alert.message}
          </div>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <button className="alert-cta">INITIATE SITE PROTOCOL</button>
        <button
          onClick={() => setVisible(false)}
          aria-label="Dismiss alert"
          style={{ background: 'none', border: 'none', color: 'var(--text-mid)', cursor: 'pointer', fontSize: 16 }}
        >
          ✕
        </button>
      </div>
    </div>
  );
}

export default AlertBanner;
