export declare function decodePng(buf: Buffer): { width: number; height: number; data: Buffer };
export declare function encodePng(img: { width: number; height: number; data: Uint8Array }): Buffer;
