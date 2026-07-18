import { useState, useEffect } from 'react';
import staticRawData from './data/rawSensorData.json';
import staticNpuData from './data/npuProcessedData.json';
import Header from './components/Header.jsx';
import AlertBanner from './components/AlertBanner.jsx';
import LiveSchematic from './components/LiveSchematic.jsx';
import TelemetryStream from './components/TelemetryStream.jsx';
import NodeDeploymentStatus from './components/NodeDeploymentStatus.jsx';
import FatigueAnalytics from './components/FatigueAnalytics.jsx';
import AIExplanation from './components/AIExplanation.jsx';
import './App.css';

function App() {
  const [rawData, setRawData] = useState(staticRawData);
  const [npuData, setNpuData] = useState(staticNpuData);
  const [wsStatus, setWsStatus] = useState('connecting');
  const [activeNodeId, setActiveNodeId] = useState('NODE_03');

  useEffect(() => {
    if (rawData.active_alert && rawData.active_alert.node_id) {
      setActiveNodeId(rawData.active_alert.node_id);
    }
  }, [rawData.active_alert]);

  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const rawRes = await fetch('http://localhost:8000/api/raw-data');
        const processedRes = await fetch('http://localhost:8000/api/processed-data');
        if (rawRes.ok && processedRes.ok) {
          const rawJson = await rawRes.json();
          const processedJson = await processedRes.json();
          setRawData(rawJson);
          setNpuData(processedJson);
        }
      } catch (err) {
        console.warn('Backend server not reachable on http://localhost:8000. Using local mock/cache data.', err);
      }
    };

    fetchInitialData();

    let ws = null;
    let reconnectTimeout = null;

    const connectWS = () => {
      ws = new WebSocket('ws://localhost:8000/ws/telemetry');

      ws.onopen = () => {
        console.log('Connected to BridgeSense WebSocket telemetry stream');
        setWsStatus('connected');
      };

      ws.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.type === 'update') {
            setRawData(payload.raw_data);
            setNpuData(payload.processed_data);
          }
        } catch (err) {
          console.error('Error parsing WebSocket message:', err);
        }
      };

      ws.onclose = () => {
        console.warn('BridgeSense WebSocket disconnected. Retrying in 3 seconds...');
        setWsStatus('disconnected');
        reconnectTimeout = setTimeout(connectWS, 3000);
      };

      ws.onerror = (err) => {
        console.error('WebSocket encountered an error:', err);
        ws.close();
      };
    };

    connectWS();

    return () => {
      if (ws) ws.close();
      if (reconnectTimeout) clearTimeout(reconnectTimeout);
    };
  }, []);

  const npuByNode = Object.fromEntries(
    npuData.node_inferences.map((n) => [n.node_id, n])
  );

  return (
    <div className="app">
      <Header bridge={rawData.bridge} model={npuData.model} />
      
      <div className="ws-status-bar" style={{
        padding: '8px 16px',
        fontSize: '11px',
        fontFamily: 'var(--mono)',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        borderBottom: '1px solid var(--panel-border)',
        borderRadius: '6px',
        background: 'var(--panel)',
        marginBottom: '16px'
      }}>
        <span className={`status-dot ${wsStatus === 'connected' ? 'good' : 'warning'} pulse`} style={{ width: 8, height: 8 }} />
        <span style={{ color: 'var(--text-mid)' }}>
          WIFI LINK STATUS: {wsStatus === 'connected' ? 'ACTIVE (Real-time stream connected)' : 'STANDALONE MODE (Searching for server on port 8000...)'}
        </span>
      </div>

      <AlertBanner alert={rawData.active_alert} />

      <div className="grid-top">
        <LiveSchematic bridge={rawData.bridge} nodes={rawData.nodes} npuByNode={npuByNode} />
        <TelemetryStream cluster={rawData.cluster_telemetry} />
      </div>

      <AIExplanation 
        nodes={rawData.nodes} 
        npuByNode={npuByNode} 
        activeNodeId={activeNodeId} 
        setActiveNodeId={setActiveNodeId} 
      />

      <NodeDeploymentStatus nodes={rawData.nodes} npuByNode={npuByNode} />

      <FatigueAnalytics fatigue={npuData.fatigue_analysis} model={npuData.model} />
    </div>
  );
}

export default App;
