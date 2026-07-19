import { useState } from 'react';

function statusLabel(s) {
  if (s === 'critical') return 'CRITICAL';
  if (s === 'warning') return 'WARNING';
  return 'NOMINAL';
}

function LiveSchematic({ bridge, nodes, npuByNode }) {
  const [hovered, setHovered] = useState(nodes.find((n) => n.status === 'critical')?.id || null);
  const hoveredNode = nodes.find((n) => n.id === hovered);
  const hoveredNpu = hoveredNode ? npuByNode[hoveredNode.id] : null;

  return (
    <section className="panel">
      <div className="panel-head">
        <div className="eyebrow">LIVE SCHEMATIC</div>
        <div className="panel-title-row">
          <h3 className="panel-title">{bridge.name}: {bridge.sector}</h3>
          <div style={{ display: 'flex', gap: 8 }}>
            <span className="tag-btn">ZOOM: 1.5x</span>
            <span className="tag-btn">LAYER: TENSION_CORES</span>
          </div>
        </div>
      </div>

      <div className="schematic-body">
        <div className="schematic-frame">
          <svg viewBox="0 0 700 340" width="100%" height="340" style={{ display: 'block' }}>
            <line x1="40" y1="270" x2="660" y2="270" stroke="#1f6f8a" strokeWidth="2.5" opacity="0.7" />
            <line x1="160" y1="60" x2="160" y2="280" stroke="#2a8bab" strokeWidth="4" opacity="0.8" />
            <line x1="160" y1="60" x2="160" y2="280" stroke="#2a8bab" strokeWidth="4" opacity="0.8" />
            <line x1="540" y1="60" x2="540" y2="280" stroke="#2a8bab" strokeWidth="4" opacity="0.8" />
            <path d="M20 270 Q160 60 350 190 Q540 60 680 270" fill="none" stroke="#4fd8ff" strokeWidth="1.6" opacity="0.85" />
            <path d="M20 270 Q160 90 350 210 Q540 90 680 270" fill="none" stroke="#2a8bab" strokeWidth="1" opacity="0.5" />
            {[80, 120, 200, 260, 300, 400, 440, 480, 560, 600].map((x, i) => (
              <line key={i} x1={x} y1="270"
                y2={x < 350 ? 60 + Math.abs(x - 160) * 0.55 : 60 + Math.abs(x - 540) * 0.55}
                x2={x} stroke="#1f6f8a" strokeWidth="0.8" opacity="0.5" />
            ))}
            <text x="30" y="300" fill="#3a4652" fontFamily="JetBrains Mono, monospace" fontSize="9">SEGMENT 4C · TENSION CORE OVERLAY</text>
          </svg>

          {nodes.map((n) => (
            <div
              key={n.id}
              className="node-hotspot"
              onMouseEnter={() => setHovered(n.id)}
              style={{
                position: 'absolute',
                left: `${(n.x / 700) * 100}%`,
                top: `${(n.y / 340) * 100}%`,
                transform: 'translate(-50%, -50%)',
              }}
            >
              <span
                className={`status-dot ${n.status} ${n.status !== 'good' ? 'pulse' : ''}`}
                style={{ width: n.status === 'critical' ? 13 : 10, height: n.status === 'critical' ? 13 : 10 }}
              />
            </div>
          ))}

          {hoveredNode && (
            <div
              className={`node-tooltip ${hoveredNode.status}`}
              style={{
                left: `${(hoveredNode.x / 700) * 100}%`,
                top: `${(hoveredNode.y / 340) * 100}%`,
              }}
            >
              <div className="tt-title">{hoveredNode.id} ({statusLabel(hoveredNode.status)})</div>
              <div className="tt-line">VIBRATION: {hoveredNode.latest.vibration_hz} Hz</div>
              <div className="tt-line">STRAIN: {hoveredNode.latest.strain_ue} µε</div>
              {hoveredNpu && <div className="tt-line">NPU ANOMALY SCORE: {hoveredNpu.anomaly_score}</div>}
            </div>
          )}
        </div>

        <div className="schematic-legend">
          <span><span className="status-dot good" /> Nominal</span>
          <span><span className="status-dot warning" /> Early drift</span>
          <span><span className="status-dot critical" /> Structural anomaly</span>
        </div>
      </div>
    </section>
  );
}

export default LiveSchematic;
