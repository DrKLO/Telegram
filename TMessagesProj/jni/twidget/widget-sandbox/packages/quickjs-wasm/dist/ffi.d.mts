import { QuickJSEmscriptenModule, JSContextPointer, JSValuePointer, JSValueConstPointer, JSRuntimePointer, OwnedHeapCharPointer, HostRefId, IntrinsicsFlags, JSVoidPointer, JSBorrowedCharPointer, BorrowedHeapCharPointer, JSContextPointerPointer, JSValuePointerPointerPointer, UInt32Pointer, JSValueConstPointerPointer, EvalDetectModule, EvalFlags, IsEqualOp, JSValuePointerPointer, JSPromiseStateEnum } from '@jitl/quickjs-ffi-types';

/**
 * Low-level FFI bindings to QuickJS's Emscripten module.
 * See instead {@link QuickJSContext}, the public Javascript interface exposed by this
 * library.
 *
 * @unstable The FFI interface is considered private and may change.
 */
declare class QuickJSFFI {
    private module;
    constructor(module: QuickJSEmscriptenModule);
    /** Set at compile time. */
    readonly DEBUG = false;
    QTS_Throw: (ctx: JSContextPointer, error: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_NewError: (ctx: JSContextPointer) => JSValuePointer;
    QTS_RuntimeSetMemoryLimit: (rt: JSRuntimePointer, limit: number) => void;
    QTS_RuntimeComputeMemoryUsage: (rt: JSRuntimePointer, ctx: JSContextPointer) => JSValuePointer;
    QTS_RuntimeDumpMemoryUsage: (rt: JSRuntimePointer) => OwnedHeapCharPointer;
    QTS_RecoverableLeakCheck: () => number;
    QTS_BuildIsSanitizeLeak: () => number;
    QTS_RuntimeSetMaxStackSize: (rt: JSRuntimePointer, stack_size: number) => void;
    QTS_GetUndefined: () => JSValueConstPointer;
    QTS_GetNull: () => JSValueConstPointer;
    QTS_GetFalse: () => JSValueConstPointer;
    QTS_GetTrue: () => JSValueConstPointer;
    QTS_NewHostRef: (ctx: JSContextPointer, id: HostRefId) => JSValuePointer;
    QTS_GetHostRefId: (value: JSValuePointer | JSValueConstPointer) => HostRefId;
    QTS_NewRuntime: () => JSRuntimePointer;
    QTS_FreeRuntime: (rt: JSRuntimePointer) => void;
    QTS_NewContext: (rt: JSRuntimePointer, intrinsics: IntrinsicsFlags) => JSContextPointer;
    QTS_FreeContext: (ctx: JSContextPointer) => void;
    QTS_FreeValuePointer: (ctx: JSContextPointer, value: JSValuePointer) => void;
    QTS_FreeValuePointerRuntime: (rt: JSRuntimePointer, value: JSValuePointer) => void;
    QTS_FreeVoidPointer: (ctx: JSContextPointer, ptr: JSVoidPointer) => void;
    QTS_FreeCString: (ctx: JSContextPointer, str: JSBorrowedCharPointer) => void;
    QTS_DupValuePointer: (ctx: JSContextPointer, val: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_NewObject: (ctx: JSContextPointer) => JSValuePointer;
    QTS_NewObjectProto: (ctx: JSContextPointer, proto: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_NewArray: (ctx: JSContextPointer) => JSValuePointer;
    QTS_NewArrayBuffer: (ctx: JSContextPointer, buffer: JSVoidPointer, length: number) => JSValuePointer;
    QTS_NewFloat64: (ctx: JSContextPointer, num: number) => JSValuePointer;
    QTS_GetFloat64: (ctx: JSContextPointer, value: JSValuePointer | JSValueConstPointer) => number;
    QTS_NewString: (ctx: JSContextPointer, string: BorrowedHeapCharPointer) => JSValuePointer;
    QTS_GetString: (ctx: JSContextPointer, value: JSValuePointer | JSValueConstPointer) => JSBorrowedCharPointer;
    QTS_GetArrayBuffer: (ctx: JSContextPointer, data: JSValuePointer | JSValueConstPointer) => JSVoidPointer;
    QTS_GetArrayBufferLength: (ctx: JSContextPointer, data: JSValuePointer | JSValueConstPointer) => number;
    QTS_NewSymbol: (ctx: JSContextPointer, description: BorrowedHeapCharPointer, isGlobal: number) => JSValuePointer;
    QTS_GetSymbolDescriptionOrKey: (ctx: JSContextPointer, value: JSValuePointer | JSValueConstPointer) => JSBorrowedCharPointer;
    QTS_IsGlobalSymbol: (ctx: JSContextPointer, value: JSValuePointer | JSValueConstPointer) => number;
    QTS_IsJobPending: (rt: JSRuntimePointer) => number;
    QTS_ExecutePendingJob: (rt: JSRuntimePointer, maxJobsToExecute: number, lastJobContext: JSContextPointerPointer) => JSValuePointer;
    QTS_GetProp: (ctx: JSContextPointer, this_val: JSValuePointer | JSValueConstPointer, prop_name: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_GetPropNumber: (ctx: JSContextPointer, this_val: JSValuePointer | JSValueConstPointer, prop_name: number) => JSValuePointer;
    QTS_SetProp: (ctx: JSContextPointer, this_val: JSValuePointer | JSValueConstPointer, prop_name: JSValuePointer | JSValueConstPointer, prop_value: JSValuePointer | JSValueConstPointer) => void;
    QTS_DefineProp: (ctx: JSContextPointer, this_val: JSValuePointer | JSValueConstPointer, prop_name: JSValuePointer | JSValueConstPointer, prop_value: JSValuePointer | JSValueConstPointer, get: JSValuePointer | JSValueConstPointer, set: JSValuePointer | JSValueConstPointer, configurable: boolean, enumerable: boolean, has_value: boolean) => void;
    QTS_GetOwnPropertyNames: (ctx: JSContextPointer, out_ptrs: JSValuePointerPointerPointer, out_len: UInt32Pointer, obj: JSValuePointer | JSValueConstPointer, flags: number) => JSValuePointer;
    QTS_Call: (ctx: JSContextPointer, func_obj: JSValuePointer | JSValueConstPointer, this_obj: JSValuePointer | JSValueConstPointer, argc: number, argv_ptrs: JSValueConstPointerPointer) => JSValuePointer;
    QTS_ResolveException: (ctx: JSContextPointer, maybe_exception: JSValuePointer) => JSValuePointer;
    QTS_Dump: (ctx: JSContextPointer, obj: JSValuePointer | JSValueConstPointer) => JSBorrowedCharPointer;
    QTS_Eval: (ctx: JSContextPointer, js_code: BorrowedHeapCharPointer, js_code_length: number, filename: string, detectModule: EvalDetectModule, evalFlags: EvalFlags) => JSValuePointer;
    QTS_GetModuleNamespace: (ctx: JSContextPointer, module_func_obj: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_Typeof: (ctx: JSContextPointer, value: JSValuePointer | JSValueConstPointer) => OwnedHeapCharPointer;
    QTS_GetLength: (ctx: JSContextPointer, out_len: UInt32Pointer, value: JSValuePointer | JSValueConstPointer) => number;
    QTS_IsEqual: (ctx: JSContextPointer, a: JSValuePointer | JSValueConstPointer, b: JSValuePointer | JSValueConstPointer, op: IsEqualOp) => number;
    QTS_GetGlobalObject: (ctx: JSContextPointer) => JSValuePointer;
    QTS_NewPromiseCapability: (ctx: JSContextPointer, resolve_funcs_out: JSValuePointerPointer) => JSValuePointer;
    QTS_PromiseState: (ctx: JSContextPointer, promise: JSValuePointer | JSValueConstPointer) => JSPromiseStateEnum;
    QTS_PromiseResult: (ctx: JSContextPointer, promise: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_TestStringArg: (string: string) => void;
    QTS_GetDebugLogEnabled: (rt: JSRuntimePointer) => number;
    QTS_SetDebugLogEnabled: (rt: JSRuntimePointer, is_enabled: number) => void;
    QTS_BuildIsDebug: () => number;
    QTS_BuildIsAsyncify: () => number;
    QTS_NewFunction: (ctx: JSContextPointer, name: string, arg_length: number, is_constructor: boolean, host_ref_id: HostRefId) => JSValuePointer;
    QTS_ArgvGetJSValueConstPointer: (argv: JSValuePointer | JSValueConstPointer, index: number) => JSValueConstPointer;
    QTS_RuntimeEnableInterruptHandler: (rt: JSRuntimePointer) => void;
    QTS_RuntimeDisableInterruptHandler: (rt: JSRuntimePointer) => void;
    QTS_RuntimeEnableModuleLoader: (rt: JSRuntimePointer, use_custom_normalize: number) => void;
    QTS_RuntimeDisableModuleLoader: (rt: JSRuntimePointer) => void;
    QTS_bjson_encode: (ctx: JSContextPointer, val: JSValuePointer | JSValueConstPointer) => JSValuePointer;
    QTS_bjson_decode: (ctx: JSContextPointer, data: JSValuePointer | JSValueConstPointer) => JSValuePointer;
}

export { QuickJSFFI };
