// @ts-nocheck
import { useEffect, useState } from 'react';
import { PublicClientApplication } from '@azure/msal-browser';
import { MsalProvider } from '@azure/msal-react';
import { msalConfig } from './msalConfig';

const msalInstance = new PublicClientApplication(msalConfig);

export function MsalAuthProvider({ children }) {
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {
    // Inicializar MSAL explícitamente
    msalInstance.initialize().then(() => {
      setIsInitialized(true);
    }).catch(console.error);
  }, []);

  // Esperar a que MSAL termine de inicializarse para evitar el error de "stubbed"
  if (!isInitialized) {
    return null; // O un spinner / loader si prefieres
  }

  return <MsalProvider instance={msalInstance}>{children}</MsalProvider>;
}