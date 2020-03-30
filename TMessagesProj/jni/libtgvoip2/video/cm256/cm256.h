/*
    C++ version:
    Copyright (c) 2016 Edouard M. Griffiths.  All rights reserved.

	Copyright (c) 2015 Christopher A. Taylor.  All rights reserved.

	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions are met:

	* Redistributions of source code must retain the above copyright notice,
	  this list of conditions and the following disclaimer.
	* Redistributions in binary form must reproduce the above copyright notice,
	  this list of conditions and the following disclaimer in the documentation
	  and/or other materials provided with the distribution.
	* Neither the name of CM256 nor the names of its contributors may be
	  used to endorse or promote products derived from this software without
	  specific prior written permission.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
	AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
	IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
	ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
	LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
	CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
	SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
	INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
	CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
	ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
	POSSIBILITY OF SUCH DAMAGE.
*/

#ifndef CM256_H
#define CM256_H

#include <assert.h>
#include "gf256.h"
#include "export.h"

class CM256CC_API CM256
{
public:
    // Encoder parameters
    typedef struct cm256_encoder_params_t {
        // Original block count < 256
        int OriginalCount;

        // Recovery block count < 256
        int RecoveryCount;

        // Number of bytes per block (all blocks are the same size in bytes)
        int BlockBytes;
    } cm256_encoder_params;

    // Descriptor for data block
    typedef struct cm256_block_t {
        // Pointer to data received.
        void* Block;

        // Block index.
        // For original data, it will be in the range
        //    [0..(originalCount-1)] inclusive.
        // For recovery data, the first one's Index must be originalCount,
        //    and it will be in the range
        //    [originalCount..(originalCount+recoveryCount-1)] inclusive.
        unsigned char Index;
        // Ignored during encoding, required during decoding.
    } cm256_block;

    CM256();
    ~CM256();

    bool isInitialized() const { return m_initialized; };

    /*
     * Cauchy MDS GF(256) encode
     *
     * This produces a set of recovery blocks that should be transmitted after the
     * original data blocks.
     *
     * It takes in 'originalCount' equal-sized blocks and produces 'recoveryCount'
     * equally-sized recovery blocks.
     *
     * The input 'originals' array allows more natural usage of the library.
     * The output recovery blocks are stored end-to-end in 'recoveryBlocks'.
     * 'recoveryBlocks' should have recoveryCount * blockBytes bytes available.
     *
     * Precondition: originalCount + recoveryCount <= 256
     *
     * When transmitting the data, the block index of the data should be sent,
     * and the recovery block index is also needed.  The decoder should also
     * be provided with the values of originalCount, recoveryCount and blockBytes.
     *
     * Example wire format:
     * [originalCount(1 byte)] [recoveryCount(1 byte)]
     * [blockIndex(1 byte)] [blockData(blockBytes bytes)]
     *
     * Be careful not to mix blocks from different encoders.
     *
     * It is possible to support variable-length data by including the original
     * data length at the front of each message in 2 bytes, such that when it is
     * recovered after a loss the data length is available in the block data and
     * the remaining bytes of padding can be neglected.
     *
     * Returns 0 on success, and any other code indicates failure.
     */
    int cm256_encode(
        cm256_encoder_params params, // Encoder parameters
        cm256_block* originals,      // Array of pointers to original blocks
        void* recoveryBlocks);       // Output recovery blocks end-to-end

    /*
     * Cauchy MDS GF(256) decode
     *
     * This recovers the original data from the recovery data in the provided
     * blocks.  There should be 'originalCount' blocks in the provided array.
     * Recovery will always be possible if that many blocks are received.
     *
     * Provide the same values for 'originalCount', 'recoveryCount', and
     * 'blockBytes' used by the encoder.
     *
     * The block Index should be set to the block index of the original data,
     * as described in the cm256_block struct comments above.
     *
     * Recovery blocks will be replaced with original data and the Index
     * will be updated to indicate the original block that was recovered.
     *
     * Returns 0 on success, and any other code indicates failure.
     */
    int cm256_decode(
        cm256_encoder_params params, // Encoder parameters
        cm256_block* blocks);        // Array of 'originalCount' blocks as described above

    /*
     * Commodity functions
     */

    // Compute the value to put in the Index member of cm256_block
    static inline unsigned char cm256_get_recovery_block_index(cm256_encoder_params params, int recoveryBlockIndex)
    {
        assert(recoveryBlockIndex >= 0 && recoveryBlockIndex < params.RecoveryCount);
        return (unsigned char)(params.OriginalCount + recoveryBlockIndex);
    }
    static inline unsigned char cm256_get_original_block_index(cm256_encoder_params params, int originalBlockIndex)
    {
        (void) params;
        assert(originalBlockIndex >= 0 && originalBlockIndex < params.OriginalCount);
        return (unsigned char)(originalBlockIndex);
    }

private:
    class CM256CC_API CM256Decoder
    {
    public:
        CM256Decoder(gf256_ctx& gf256Ctx);
        ~CM256Decoder();

        // Encode parameters
        cm256_encoder_params Params;

        // Recovery blocks
        cm256_block* Recovery[256];
        int RecoveryCount;

        // Original blocks
        cm256_block* Original[256];
        int OriginalCount;

        // Row indices that were erased
        uint8_t ErasuresIndices[256];

        // Initialize the decoder
        bool Initialize(cm256_encoder_params& params, cm256_block* blocks);

        // Decode m=1 case
        void DecodeM1();

        // Decode for m>1 case
        void Decode();

        // Generate the LU decomposition of the matrix
        void GenerateLDUDecomposition(uint8_t* matrix_L, uint8_t* diag_D, uint8_t* matrix_U);

    private:
        gf256_ctx& m_gf256Ctx;
    };

    // Encode one block.
    // Note: This function does not validate input, use with care.
    void cm256_encode_block(
        cm256_encoder_params params, // Encoder parameters
        cm256_block* originals,      // Array of pointers to original blocks
        int recoveryBlockIndex,      // Return value from cm256_get_recovery_block_index()
        void* recoveryBlock);        // Output recovery block

    gf256_ctx m_gf256Ctx;
    bool m_initialized;
};


#endif // CM256_H
