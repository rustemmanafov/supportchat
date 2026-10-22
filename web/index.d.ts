import type { SupportChatApi, SupportChatBootOptions } from './sdk.js';
export type * from './sdk.js';

/** Loads sdk.js from your SupportChat server and boots it (when `appId` is given). */
export function loadSupportChat(options: Partial<SupportChatBootOptions> & { serverUrl: string }): Promise<SupportChatApi>;
