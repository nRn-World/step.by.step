import { Capacitor, registerPlugin } from '@capacitor/core';

/** The connected session token lives here, outside the component module, so React fast refresh keeps working. */
let accessToken = '';

// Native only (android/.../WearBridgePlugin.java): passes the token to a paired Wear OS watch.
const WearBridge = registerPlugin<{ setToken(o: { token: string }): Promise<void> }>('WearBridge');

export const setAccessToken = (token: string) => {
  accessToken = token;
  if (Capacitor.getPlatform() === 'android') WearBridge.setToken({ token }).catch(() => {});
};
export const getAccessToken = () => accessToken;
