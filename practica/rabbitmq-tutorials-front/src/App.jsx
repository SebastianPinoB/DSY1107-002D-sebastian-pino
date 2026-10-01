import { useState, useEffect, useRef } from 'react';
import './App.css';

function App() {
  const [orders, setOrders] = useState([]);
  const [status, setStatus] = useState('Conectando...');
  const [stats, setStats] = useState({ total: 0, success: 0, failed: 0 });
  
  // Usamos una referencia para generar IDs únicos y puros sin romper las reglas de React
  const orderCounter = useRef(1);

  // Cargar estado inicial y configurar el intervalo de sondeo (polling) de forma segura
  useEffect(() => {
    let isMounted = true;

    const checkBackendStatus = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/orders/status');
        if (response.ok && isMounted) {
          setStatus('✓ Conectado a Backend');
        }
      } catch {
        if (isMounted) {
          setStatus('✗ Backend desconectado');
        }
      }
    };

    // Deferimos la primera llamada para evitar actualizaciones síncronas en el render inicial
    const timeoutId = setTimeout(checkBackendStatus, 0);
    const interval = setInterval(checkBackendStatus, 5000);

    return () => {
      isMounted = false;
      clearTimeout(timeoutId);
      clearInterval(interval);
    };
  }, []);

  const sendOrder = async (customerName) => {
    const orderId = `ORD-${orderCounter.current++}`;
    
    try {
      const response = await fetch('http://localhost:8080/api/orders/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, customerName }),
      });

      if (response.ok) {
        const data = await response.json();
        
        const newOrder = {
          id: orderId,
          customer: customerName,
          message: data.message,
          timestamp: new Date().toLocaleTimeString(),
          status: 'ENVIADA',
        };

        setOrders(prevOrders => [newOrder, ...prevOrders]);
        setStats(prev => ({ ...prev, total: prev.total + 1 }));

        setTimeout(() => {
          updateOrderStatus(orderId);
        }, 2000);
      }
    } catch (error) {
      console.error('Error:', error);
      alert('Error al enviar la orden');
    }
  };

  const updateOrderStatus = (orderId) => {
    // Alternativa basada en el ID para mantener la simulación sin romper reglas de pureza
    const numericId = parseInt(orderId.replace('ORD-', ''), 10);
    const isSuccess = numericId % 2 !== 0; // Alterna éxito y fallo de forma determinista
    
    setOrders(prevOrders =>
      prevOrders.map(order =>
        order.id === orderId
          ? {
              ...order,
              status: isSuccess ? 'PROCESADA' : 'FALLIDA (DLQ)',
            }
          : order
      )
    );

    if (isSuccess) {
      setStats(prev => ({ ...prev, success: prev.success + 1 }));
    } else {
      setStats(prev => ({ ...prev, failed: prev.failed + 1 }));
    }
  };

  return (
    <div className="App">
      <header className="header">
        <h1>Sistema de Órdenes con RabbitMQ</h1>
        <p className="status-badge">{status}</p>
      </header>

      <section className="control-panel">
        <h2>Enviar Órdenes</h2>
        <div className="button-group">
          <button onClick={() => sendOrder('Juan Pérez')} className="btn btn-primary">
            Orden Cliente 1
          </button>
          <button onClick={() => sendOrder('María García')} className="btn btn-primary">
            Orden Cliente 2
          </button>
          <button onClick={() => sendOrder('Carlos López')} className="btn btn-primary">
            Orden Cliente 3
          </button>
        </div>
      </section>

      <section className="stats-panel">
        <div className="stat-card">
          <div className="stat-number">{stats.total}</div>
          <div className="stat-label">Total Órdenes</div>
        </div>
        <div className="stat-card success">
          <div className="stat-number">{stats.success}</div>
          <div className="stat-label">Procesadas</div>
        </div>
        <div className="stat-card error">
          <div className="stat-number">{stats.failed}</div>
          <div className="stat-label">En DLQ</div>
        </div>
      </section>

      <section className="orders-list">
        <h2>Historial de Órdenes</h2>
        {orders.length === 0 ? (
          <p className="empty-message">No hay órdenes aún. ¡Envía una!</p>
        ) : (
          <div className="orders-table">
            {orders.map(order => (
              <div key={order.id} className={`order-item status-${order.status.toLowerCase().replace(/[^a-z]/g, '')}`}>
                <div className="order-header">
                  <strong>{order.id}</strong>
                  <span className="status-badge">{order.status}</span>
                </div>
                <div className="order-detail">
                  <span>Cliente: {order.customer}</span>
                  <span className="timestamp">{order.timestamp}</span>
                </div>
                <div className="order-message">{order.message}</div>
              </div>
            ))}
          </div>
        )}
      </section>

      <footer className="footer">
        <p>Tip: Abre la consola de RabbitMQ (localhost:15672) para ver las colas en tiempo real</p>
      </footer>
    </div>
  );
}

export default App;