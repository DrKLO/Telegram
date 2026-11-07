export interface Image { width: number; height: number; data: Uint8Array }
export declare const DIFF: { maxRatio: number; threshold: number };
export declare function diffStats(a: Image, b: Image, threshold?: number): { ink: number; area: number; image: { width: number; height: number; data: Uint8Array } };
export declare function colorCount(img: Image, hex: string, tol?: number): number;
export declare function colorsMatch(got: Image, ref: Image, colors: string[]): { ok: boolean; detail: string };
export declare function judgeStage(
  got: Image,
  refs: { engine: string; png: Image }[],
  stage: { maxDiff?: number; colors?: string[]; xfail?: string },
): { status: 'pass' | 'fail' | 'xfail' | 'xpass' | 'skip'; diff: number | null; ref: string | null; maxDiff: number; detail?: string; image: { width: number; height: number; data: Uint8Array } | null };
