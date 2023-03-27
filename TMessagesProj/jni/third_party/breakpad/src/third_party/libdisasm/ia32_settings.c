#include "libdis.h"
#include "ia32_settings.h"
#include "ia32_reg.h"
#include "ia32_insn.h"

ia32_settings_t ia32_settings = {
	1, 0xF4, 
	MAX_INSTRUCTION_SIZE,
	4, 4, 8, 4, 8,
	REG_ESP_INDEX, REG_EBP_INDEX, REG_EIP_INDEX, REG_FLAGS_INDEX,
	REG_DWORD_OFFSET, REG_SEG_OFFSET, REG_FPU_OFFSET,
	opt_none
};
