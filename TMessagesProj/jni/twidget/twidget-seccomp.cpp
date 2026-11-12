// The widget engine process's own seccomp-bpf filter (WidgetEngineService#onCreate), on top of SELinux isolated_app and
// the zygote's app filter: code that escapes QuickJS should find no way to start programs or processes, and as little
// of the kernel's attack surface (perf events, eBPF, userfaultfd, io_uring, keyrings, ptrace) as possible.
//
// Two modes. Audit: the listed calls trap (SIGSYS); the handler records them, logged by a thread as "TwidgetSeccomp"
// lines, and makes the call itself through an escape hatch the filter lets through (instruction_pointer at
// twidget_sc_gadget_ip), so nothing changes but the log. Process creation and exec are refused (EPERM) even then: they
// cannot be redone from a signal handler, and the engine never needs them. ioctl is not filtered: ART's GC makes
// userfaultfd ioctls from threads with SIGSYS blocked, where a trap kills the process (the kernel resets the handler),
// so even auditing it is unsafe; narrowing ioctl needs another way. Enforce: the dangerous calls kill the process
// (the client restarts the engine), other sockets than AF_UNIX get EPERM, clone3 ENOSYS (bionic falls back to clone,
// whose flags the filter can read).
//
// Needs SECCOMP_FILTER_FLAG_TSYNC (Linux 3.17; every device from Android 8): ART's threads exist before the service
// starts. Without it the engine runs unfiltered, which is logged.

#include <jni.h>
#include <android/log.h>
#include <asm/unistd.h>
#include <errno.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <ucontext.h>
#include <unistd.h>

#include <atomic>
#include <vector>

#define TAG "TwidgetSeccomp"

#ifndef SECCOMP_RET_KILL_PROCESS
#define SECCOMP_RET_KILL_PROCESS 0x80000000U
#endif
#ifndef SECCOMP_FILTER_FLAG_TSYNC
#define SECCOMP_FILTER_FLAG_TSYNC 1
#endif
#ifndef SECCOMP_SET_MODE_FILTER
#define SECCOMP_SET_MODE_FILTER 1
#endif
#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

extern "C" long twidget_sc_gadget(long nr, long a0, long a1, long a2, long a3, long a4, long a5);
extern "C" char twidget_sc_gadget_ip[];

// The escape hatch: one syscall instruction, at a known address right before twidget_sc_gadget_ip (where the kernel
// reports the instruction pointer of a syscall, after its instruction).
#if defined(__aarch64__)
#define TW_ARCH AUDIT_ARCH_AARCH64
asm(R"(
    .text
    .p2align 2
    .globl twidget_sc_gadget
    .hidden twidget_sc_gadget
    .type twidget_sc_gadget, %function
twidget_sc_gadget:
    mov x8, x0
    mov x0, x1
    mov x1, x2
    mov x2, x3
    mov x3, x4
    mov x4, x5
    mov x5, x6
    svc #0
    .globl twidget_sc_gadget_ip
    .hidden twidget_sc_gadget_ip
twidget_sc_gadget_ip:
    ret
    .size twidget_sc_gadget, . - twidget_sc_gadget
)");
#elif defined(__arm__)
#define TW_ARCH AUDIT_ARCH_ARM
asm(R"(
    .text
    .p2align 2
    .arm
    .globl twidget_sc_gadget
    .hidden twidget_sc_gadget
    .type twidget_sc_gadget, %function
twidget_sc_gadget:
    push {r4, r5, r7, lr}
    mov r7, r0
    mov r0, r1
    mov r1, r2
    mov r2, r3
    ldr r3, [sp, #16]
    ldr r4, [sp, #20]
    ldr r5, [sp, #24]
    svc #0
    .globl twidget_sc_gadget_ip
    .hidden twidget_sc_gadget_ip
twidget_sc_gadget_ip:
    pop {r4, r5, r7, pc}
    .size twidget_sc_gadget, . - twidget_sc_gadget
)");
#elif defined(__x86_64__)
#define TW_ARCH AUDIT_ARCH_X86_64
asm(R"(
    .text
    .globl twidget_sc_gadget
    .hidden twidget_sc_gadget
    .type twidget_sc_gadget, @function
twidget_sc_gadget:
    mov %rdi, %rax
    mov %rsi, %rdi
    mov %rdx, %rsi
    mov %rcx, %rdx
    mov %r8, %r10
    mov %r9, %r8
    mov 8(%rsp), %r9
    syscall
    .globl twidget_sc_gadget_ip
    .hidden twidget_sc_gadget_ip
twidget_sc_gadget_ip:
    ret
    .size twidget_sc_gadget, . - twidget_sc_gadget
)");
#elif defined(__i386__)
#define TW_ARCH AUDIT_ARCH_I386
asm(R"(
    .text
    .globl twidget_sc_gadget
    .hidden twidget_sc_gadget
    .type twidget_sc_gadget, @function
twidget_sc_gadget:
    push %ebp
    push %edi
    push %esi
    push %ebx
    mov 20(%esp), %eax
    mov 24(%esp), %ebx
    mov 28(%esp), %ecx
    mov 32(%esp), %edx
    mov 36(%esp), %esi
    mov 40(%esp), %edi
    mov 44(%esp), %ebp
    int $0x80
    .globl twidget_sc_gadget_ip
    .hidden twidget_sc_gadget_ip
twidget_sc_gadget_ip:
    pop %ebx
    pop %esi
    pop %edi
    pop %ebp
    ret
    .size twidget_sc_gadget, . - twidget_sc_gadget
)");
#else
#error "twidget seccomp: unknown architecture"
#endif

namespace {

enum Kind {
    KILL,     // never needed; enforce kills the process
    NO_PROC,  // process creation, exec: EPERM in both modes
};

struct Rule {
    int nr;
    const char *name;
    Kind kind;
};

#define R(name, kind) {__NR_##name, #name, kind}
const Rule RULES[] = {
    R(execve, NO_PROC),
#ifdef __NR_execveat
    R(execveat, NO_PROC),
#endif
#ifdef __NR_fork
    R(fork, NO_PROC),
#endif
#ifdef __NR_vfork
    R(vfork, NO_PROC),
#endif
    R(ptrace, KILL),
    R(process_vm_readv, KILL),
    R(process_vm_writev, KILL),
    R(perf_event_open, KILL),
#ifdef __NR_bpf
    R(bpf, KILL),
#endif
#ifdef __NR_io_uring_setup
    R(io_uring_setup, KILL),
    R(io_uring_enter, KILL),
    R(io_uring_register, KILL),
#endif
    R(add_key, KILL),
    R(request_key, KILL),
    R(keyctl, KILL),
    R(kexec_load, KILL),
#ifdef __NR_kexec_file_load
    R(kexec_file_load, KILL),
#endif
    R(init_module, KILL),
    R(finit_module, KILL),
    R(delete_module, KILL),
    R(unshare, KILL),
    R(setns, KILL),
    R(mount, KILL),
    R(umount2, KILL),
    R(pivot_root, KILL),
    R(chroot, KILL),
    R(swapon, KILL),
    R(swapoff, KILL),
    R(reboot, KILL),
    R(acct, KILL),
    R(name_to_handle_at, KILL),
    R(open_by_handle_at, KILL),
#ifdef __NR_iopl
    R(iopl, KILL),
#endif
#ifdef __NR_ioperm
    R(ioperm, KILL),
#endif
#ifdef __NR_modify_ldt
    R(modify_ldt, KILL),
#endif
};
#undef R

bool enforcing;
// Our traps carry this in SECCOMP_RET_DATA (si_errno): SIGSYS also comes from the zygote's own app filter, whose calls
// go to the handler that was there before (debuggerd's), as without ours.
constexpr uint32_t TRAP_DATA = 0x5eed;
struct sigaction previous;

// What the handler saw, async-signal-safe: counts by syscall number.
constexpr int MAX_NR = 1024;
std::atomic<uint32_t> counts[MAX_NR];

const char *nameOf(int nr) {
    for (const Rule &rule : RULES)
        if (rule.nr == nr) return rule.name;
#ifdef __NR_socket
    if (nr == __NR_socket) return "socket";
#endif
#ifdef __NR_socketpair
    if (nr == __NR_socketpair) return "socketpair";
#endif
    if (nr == __NR_clone) return "clone";
#ifdef __NR_userfaultfd
    if (nr == __NR_userfaultfd) return "userfaultfd";
#endif
    return "?";
}

bool noProcess(int nr) {
    for (const Rule &rule : RULES)
        if (rule.nr == nr) return rule.kind == NO_PROC;
    return nr == __NR_clone;  // only a clone without CLONE_THREAD traps
}

void setResult(void *context, long result) {
    auto *uc = static_cast<ucontext_t *>(context);
#if defined(__aarch64__)
    uc->uc_mcontext.regs[0] = result;
#elif defined(__arm__)
    uc->uc_mcontext.arm_r0 = result;
#elif defined(__x86_64__)
    uc->uc_mcontext.gregs[REG_RAX] = result;
#elif defined(__i386__)
    uc->uc_mcontext.gregs[REG_EAX] = result;
#endif
}

std::atomic<uint32_t> systemBlocked[MAX_NR];

void onSigsys(int signal, siginfo_t *info, void *context) {
    // Our own call, redone in the audit handler (SA_NODEFER brings it here): the system's filter refuses it too, and
    // stacked filters report the newest one's data, ours. Without our filter the original call would have hit the
    // system's trap; the engine gets ENOSYS instead, and the log says so.
    if (info->si_call_addr == static_cast<void *>(twidget_sc_gadget_ip)) {
        int nr = info->si_syscall;
        if (nr >= 0 && nr < MAX_NR) systemBlocked[nr].fetch_add(1, std::memory_order_relaxed);
        setResult(context, -ENOSYS);
        return;
    }
    if (info->si_errno != static_cast<int>(TRAP_DATA)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "foreign SIGSYS: nr %d arch 0x%x data 0x%x", info->si_syscall,
                info->si_arch, info->si_errno);
        if ((previous.sa_flags & SA_SIGINFO) && previous.sa_sigaction) {
            previous.sa_sigaction(signal, info, context);
        } else if (previous.sa_handler != SIG_IGN && previous.sa_handler != SIG_DFL && previous.sa_handler) {
            previous.sa_handler(signal);
        } else {
            sigaction(SIGSYS, &previous, nullptr);
            raise(SIGSYS);
        }
        return;
    }
    int saved = errno;
    auto *uc = static_cast<ucontext_t *>(context);
    long nr = info->si_syscall, a[6];
#if defined(__aarch64__)
    for (int i = 0; i < 6; i++) a[i] = uc->uc_mcontext.regs[i];
#elif defined(__arm__)
    a[0] = uc->uc_mcontext.arm_r0; a[1] = uc->uc_mcontext.arm_r1; a[2] = uc->uc_mcontext.arm_r2;
    a[3] = uc->uc_mcontext.arm_r3; a[4] = uc->uc_mcontext.arm_r4; a[5] = uc->uc_mcontext.arm_r5;
#elif defined(__x86_64__)
    const greg_t *g = uc->uc_mcontext.gregs;
    a[0] = g[REG_RDI]; a[1] = g[REG_RSI]; a[2] = g[REG_RDX]; a[3] = g[REG_R10]; a[4] = g[REG_R8]; a[5] = g[REG_R9];
#elif defined(__i386__)
    const greg_t *g = uc->uc_mcontext.gregs;
    a[0] = g[REG_EBX]; a[1] = g[REG_ECX]; a[2] = g[REG_EDX]; a[3] = g[REG_ESI]; a[4] = g[REG_EDI]; a[5] = g[REG_EBP];
#endif
    if (nr >= 0 && nr < MAX_NR) counts[nr].fetch_add(1, std::memory_order_relaxed);
    long result = noProcess(static_cast<int>(nr)) ? -EPERM : twidget_sc_gadget(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
    setResult(context, result);
    errno = saved;
}

// Logs what the handler recorded since the last pass, every two seconds.
void *reporter(void *) {
    static uint32_t logged[MAX_NR], loggedBlocked[MAX_NR];
    for (;;) {
        sleep(2);
        for (int nr = 0; nr < MAX_NR; nr++) {
            uint32_t count = counts[nr].load(std::memory_order_relaxed);
            if (count != logged[nr]) {
                __android_log_print(ANDROID_LOG_WARN, TAG, "audit: %s (%d) x%u", nameOf(nr), nr, count);
                logged[nr] = count;
            }
            uint32_t blocked = systemBlocked[nr].load(std::memory_order_relaxed);
            if (blocked != loggedBlocked[nr]) {
                __android_log_print(ANDROID_LOG_WARN, TAG, "audit: %s (%d) refused by the system filter too, x%u",
                        nameOf(nr), nr, blocked);
                loggedBlocked[nr] = blocked;
            }
        }
    }
    return nullptr;
}

// The filter as BPF, built at run time for the gadget's address.
struct Program {
    std::vector<sock_filter> code;
    void op(uint16_t code_, uint32_t k, uint8_t jt = 0, uint8_t jf = 0) { code.push_back({code_, jt, jf, k}); }
    void load(uint32_t offset) { op(BPF_LD | BPF_W | BPF_ABS, offset); }
    void ret(uint32_t value) { op(BPF_RET | BPF_K, value); }
    // if (A == value) return result
    void ifEqual(uint32_t value, uint32_t result) {
        op(BPF_JMP | BPF_JEQ | BPF_K, value, 0, 1);
        ret(result);
    }
};

constexpr uint32_t argLow(int i) {
    return offsetof(seccomp_data, args) + 8 * i;  // little-endian: the low word first
}

Program build(bool enforce) {
    const uint32_t trap = SECCOMP_RET_TRAP | TRAP_DATA;
    const uint32_t kill = enforce ? SECCOMP_RET_KILL_PROCESS : trap;
    const uint32_t noProc = enforce ? (SECCOMP_RET_ERRNO | EPERM) : trap;
    const uint32_t denied = enforce ? (SECCOMP_RET_ERRNO | EPERM) : trap;
    Program p;

    // Another architecture's calls (int 0x80 from x86_64) would get around every number below.
    p.load(offsetof(seccomp_data, arch));
    p.op(BPF_JMP | BPF_JEQ | BPF_K, TW_ARCH, 1, 0);
    p.ret(enforce ? SECCOMP_RET_KILL_PROCESS : (SECCOMP_RET_ERRNO | ENOSYS));

    if (!enforce) {
        // The audit handler's own calls.
        uint64_t ip = reinterpret_cast<uintptr_t>(twidget_sc_gadget_ip);
        p.load(offsetof(seccomp_data, instruction_pointer));
        p.op(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(ip), 0, 3);
        p.load(offsetof(seccomp_data, instruction_pointer) + 4);
        p.op(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(ip >> 32), 0, 1);
        p.ret(SECCOMP_RET_ALLOW);
    }

    p.load(offsetof(seccomp_data, nr));
    for (const Rule &rule : RULES) p.ifEqual(rule.nr, rule.kind == NO_PROC ? noProc : kill);

#ifdef __NR_clone3
    // Its flags sit in memory the filter cannot read: bionic falls back to clone.
    p.ifEqual(__NR_clone3, SECCOMP_RET_ERRNO | ENOSYS);
#endif
    // clone: threads only.
    p.op(BPF_JMP | BPF_JEQ | BPF_K, __NR_clone, 0, 4);
    p.load(argLow(0));
    p.op(BPF_JMP | BPF_JSET | BPF_K, CLONE_THREAD, 0, 1);
    p.ret(SECCOMP_RET_ALLOW);
    p.ret(noProc);

#if defined(__NR_socket) && !defined(__i386__)
    // Sockets: AF_UNIX only (logd). SELinux and the missing inet group refuse the rest already. (32-bit x86 goes
    // through socketcall, whose arguments are in memory.)
    for (int nr : {__NR_socket, __NR_socketpair}) {
        p.load(offsetof(seccomp_data, nr));
        p.op(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(nr), 0, 4);
        p.load(argLow(0));
        p.op(BPF_JMP | BPF_JEQ | BPF_K, AF_UNIX, 0, 1);
        p.ret(SECCOMP_RET_ALLOW);
        p.ret(denied);
    }
#endif

#ifdef __NR_userfaultfd
    // userfaultfd: ART's concurrent mark-compact GC (Android 14+) uses it, user-mode faults only. Kernel-mode faults
    // are what makes it an exploit primitive (stalling the kernel mid-copy), and UFFD_USER_MODE_ONLY takes them away.
    p.load(offsetof(seccomp_data, nr));
    p.op(BPF_JMP | BPF_JEQ | BPF_K, __NR_userfaultfd, 0, 4);
    p.load(argLow(0));
    p.op(BPF_JMP | BPF_JSET | BPF_K, 1 /* UFFD_USER_MODE_ONLY */, 0, 1);
    p.ret(SECCOMP_RET_ALLOW);
    p.ret(kill);
#endif

    p.ret(SECCOMP_RET_ALLOW);
    return p;
}

}  // namespace

// 0 when the filter is on; else the errno of the step that failed (the engine then runs without it).
extern "C" JNIEXPORT jint JNICALL Java_org_telegram_utils_twidget_WidgetEngineService_installSeccomp(JNIEnv *, jclass,
        jboolean enforce) {
    static std::atomic<bool> installed{false};
    if (installed.exchange(true)) return 0;
    enforcing = enforce;
    if (!enforce) {
        struct sigaction action = {};
        action.sa_sigaction = onSigsys;
        action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_NODEFER;
        sigemptyset(&action.sa_mask);
        if (sigaction(SIGSYS, &action, &previous) != 0) {
            int error = errno;
            __android_log_print(ANDROID_LOG_ERROR, TAG, "no SIGSYS handler (%s): unfiltered", strerror(error));
            return error;
        }
    }
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
        int error = errno;
        __android_log_print(ANDROID_LOG_ERROR, TAG, "PR_SET_NO_NEW_PRIVS failed (%s): unfiltered", strerror(error));
        return error;
    }
    Program program = build(enforce);
    sock_fprog prog = {static_cast<unsigned short>(program.code.size()), program.code.data()};
    long result = syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC, &prog);
    if (result != 0) {
        // -1 with errno (ENOSYS/EINVAL: no seccomp or no TSYNC), or the id of a thread that could not be synced.
        int error = result < 0 ? errno : ESRCH;
        __android_log_print(ANDROID_LOG_ERROR, TAG, "seccomp filter not installed (%s, %ld): unfiltered",
                strerror(error), result);
        return error;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "filter on: %s, %zu instructions", enforce ? "enforce" : "audit",
            program.code.size());
    if (!enforce) {
        pthread_t thread;
        if (pthread_create(&thread, nullptr, reporter, nullptr) == 0) pthread_detach(thread);
    }
    return 0;
}

// Debug builds: a few calls the filter refuses, with what came back, as a log line each. Audit: each also shows up as
// an "audit:" line, then gets the kernel's own answer (exec still EPERM).
extern "C" JNIEXPORT void JNICALL Java_org_telegram_utils_twidget_WidgetEngineService_seccompSelfTest(JNIEnv *, jclass) {
    auto run = [](const char *what, long result) {
        int error = result < 0 ? errno : 0;
        __android_log_print(ANDROID_LOG_INFO, TAG, "self-test: %s -> %ld (%s)", what, result, error ? strerror(error) : "ok");
    };
#if defined(__NR_socket) && !defined(__i386__)
    {
        long fd = syscall(__NR_socket, AF_INET, SOCK_STREAM, 0);
        run("socket(AF_INET)", fd);
        if (fd >= 0) close(static_cast<int>(fd));
    }
#endif
    run("execve(NULL)", syscall(__NR_execve, nullptr, nullptr, nullptr));
    if (enforcing) return;  // the rest kills the process when enforcing
    run("perf_event_open(NULL)", syscall(__NR_perf_event_open, nullptr, 0, -1, -1, 0));
#ifdef __NR_userfaultfd
    {
        long fd = syscall(__NR_userfaultfd, 0);
        run("userfaultfd(0)", fd);
        if (fd >= 0) close(static_cast<int>(fd));
    }
#endif
}
