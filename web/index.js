/**
 * npm entry: loads the SDK from your SupportChat server, so the chat always matches the
 * server version and updates without a new npm release.
 *
 *   import { loadSupportChat } from '@supportchat/web';
 *   const SupportChat = await loadSupportChat({ serverUrl: 'https://chat.example.com', appId: 'shop' });
 *   SupportChat('identify', { id: user.id, name: user.name }, userHash);
 */
let loading = null;

export function loadSupportChat(options) {
  if (typeof window === 'undefined') return Promise.reject(new Error('loadSupportChat() runs in the browser only'));
  if (window.SupportChat && window.SupportChat.version) {
    if (options && options.appId) window.SupportChat('boot', options);
    return Promise.resolve(window.SupportChat);
  }
  if (loading) return loading;
  const { serverUrl, ...settings } = options || {};
  if (!serverUrl) return Promise.reject(new Error('loadSupportChat: serverUrl is required'));
  window.SupportChatSettings = { ...settings, baseUrl: serverUrl.replace(/\/$/, '') };
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `${window.SupportChatSettings.baseUrl}/sdk.js`;
    script.async = true;
    script.onload = () => resolve(window.SupportChat);
    script.onerror = () => {
      loading = null;
      reject(new Error(`Could not load ${script.src}`));
    };
    document.head.appendChild(script);
  });
  return loading;
}
