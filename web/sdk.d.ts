/** Types for the global `SupportChat` function installed by sdk.js. */

export interface SupportChatUser {
  /** Your own stable user id. */
  id: string | number;
  name?: string;
  email?: string;
  phone?: string;
  attributes?: Record<string, string | number | boolean | null>;
}

export interface SupportChatBootOptions {
  appId: string;
  user?: SupportChatUser | null;
  /** HMAC-SHA256(identitySecret, user.id) as hex, computed on your backend. */
  userHash?: string | null;
  attributes?: Record<string, string | number | boolean | null>;
  /** e.g. 'az', 'en', 'ru'; defaults to the browser language. */
  locale?: string;
  /** Hide the floating button and open the chat from your own UI. */
  hideLauncher?: boolean;
  /** Open the chat right after boot. */
  open?: boolean;
  /** Mount inside this element instead of <body>. */
  container?: string | HTMLElement;
}

export interface SupportChatMessage {
  id: string;
  body: string;
  authorName: string | null;
  attachments: Array<{ id: string; name: string; mime: string; size: number; url: string }>;
  createdAt: string;
}

export interface SupportChatEvents {
  ready: { visitorId: string; userId: string | null };
  open: undefined;
  close: undefined;
  unread: number;
  message: SupportChatMessage;
  error: { code: string; message?: string };
}

export interface SupportChatApi {
  (command: 'boot', options: SupportChatBootOptions): void;
  (command: 'identify', user: SupportChatUser, userHash?: string): void;
  (command: 'update', data: { user?: Omit<SupportChatUser, 'id'>; attributes?: SupportChatUser['attributes'] }): void;
  (command: 'open' | 'close' | 'toggle' | 'logout' | 'shutdown' | 'showLauncher' | 'hideLauncher'): void;
  <K extends keyof SupportChatEvents>(command: 'on' | 'off', event: K, listener: (data: SupportChatEvents[K]) => void): void;
  (command: 'unreadCount'): number;
  (command: 'isOpen'): boolean;
  version: string;
}

declare global {
  interface Window {
    SupportChat: SupportChatApi;
    SupportChatSettings?: Partial<SupportChatBootOptions> & { baseUrl?: string; autoBoot?: boolean };
  }
}
