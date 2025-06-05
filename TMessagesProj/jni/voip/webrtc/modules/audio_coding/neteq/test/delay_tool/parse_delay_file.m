%
%  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
%
%  Use of this source code is governed by a BSD-style license
%  that can be found in the LICENSE file in the root of the source
%  tree. An additional intellectual property rights grant can be found
%  in the file PATENTS.  All contributing project authors may
%  be found in the AUTHORS file in the root of the source tree.
%

function outStruct = parse_delay_file(file)

fid = fopen(file, 'rb');
if fid == -1
    error('Cannot open file %s', file);
end

textline = fgetl(fid);
if ~strncmp(textline, '#!NetEQ_Delay_Logging', 21)
    error('Wrong file format');
end

ver = sscanf(textline, '#!NetEQ_Delay_Logging%d.%d');
if ~all(ver == [2; 0])
    error('Wrong version of delay logging function')
end


start_pos = ftell(fid);
fseek(fid, -12, 'eof');
textline = fgetl(fid);
if ~strncmp(textline, 'End of file', 21)
    error('File ending is not correct. Seems like the simulation ended abnormally.');
end

fseek(fid,-12-4, 'eof');
Npackets = fread(fid, 1, 'int32');
fseek(fid, start_pos, 'bof');

rtpts = zeros(Npackets, 1);
seqno = zeros(Npackets, 1);
pt = zeros(Npackets, 1);
plen = zeros(Npackets, 1);
recin_t = nan*ones(Npackets, 1);
decode_t = nan*ones(Npackets, 1);
playout_delay = zeros(Npackets, 1);
optbuf = zeros(Npackets, 1);

fs_ix = 1;
clock = 0;
ts_ix = 1;
ended = 0;
late_packets = 0;
fs_now = 8000;
last_decode_k = 0;
tot_expand = 0;
tot_accelerate = 0;
tot_preemptive = 0;

while not(ended)
    signal = fread(fid, 1, '*int32');
    
    switch signal
        case 3 % NETEQ_DELAY_LOGGING_SIGNAL_CLOCK
            clock = fread(fid, 1, '*float32');
            
            % keep on reading batches of M until the signal is no longer "3"
            % read int32 + float32 in one go
            % this is to save execution time
            temp = [3; 0];
            M = 120;
            while all(temp(1,:) == 3)
                fp = ftell(fid);
                temp = fread(fid, [2 M], '*int32');
            end
            
            % back up to last clock event
            fseek(fid, fp - ftell(fid) + ...
                (find(temp(1,:) ~= 3, 1 ) - 2) * 2 * 4 + 4, 'cof');
            % read the last clock value
            clock = fread(fid, 1, '*float32');
            
        case 1 % NETEQ_DELAY_LOGGING_SIGNAL_RECIN
            temp_ts = fread(fid, 1, 'uint32');
            
            if late_packets > 0
                temp_ix = ts_ix - 1;
                while (temp_ix >= 1) && (rtpts(temp_ix) ~= temp_ts)
                    % TODO(hlundin): use matlab vector search instead?
                    temp_ix = temp_ix - 1;
                end
                
                if temp_ix >= 1
                    % the ts was found in the vector
                    late_packets = late_packets - 1;
                else
                    temp_ix = ts_ix;
                    ts_ix = ts_ix + 1;
                end
            else
                temp_ix = ts_ix;
                ts_ix = ts_ix + 1;
            end
            
            rtpts(temp_ix) = temp_ts;
            seqno(temp_ix) = fread(fid, 1, 'uint16');
            pt(temp_ix) = fread(fid, 1, 'int32');
            plen(temp_ix) = fread(fid, 1, 'int16');
            recin_t(temp_ix) = clock;
            
        case 2 % NETEQ_DELAY_LOGGING_SIGNAL_FLUSH
            % do nothing
            
        case 4 % NETEQ_DELAY_LOGGING_SIGNAL_EOF
            ended = 1;
            
        case 5 % NETEQ_DELAY_LOGGING_SIGNAL_DECODE
            last_decode_ts = fread(fid, 1, 'uint32');
            temp_delay = fread(fid, 1, 'uint16');
            
            k = find(rtpts(1:(ts_ix - 1))==last_decode_ts,1,'last');
            if ~isempty(k)
                decode_t(k) = clock;
                playout_delay(k) = temp_delay + ...
                    5 *  fs_now / 8000; % add overlap length
                last_decode_k = k;
            end
            
        case 6 % NETEQ_DELAY_LOGGING_SIGNAL_CHANGE_FS
            fsvec(fs_ix) = fread(fid, 1, 'uint16');
            fschange_ts(fs_ix) = last_decode_ts;
            fs_now = fsvec(fs_ix);
            fs_ix = fs_ix + 1;
            
        case 7 % NETEQ_DELAY_LOGGING_SIGNAL_MERGE_INFO
            playout_delay(last_decode_k) = playout_delay(last_decode_k) ...
                + fread(fid, 1, 'int32');
            
        case 8 % NETEQ_DELAY_LOGGING_SIGNAL_EXPAND_INFO
            temp = fread(fid, 1, 'int32');
            if last_decode_k ~= 0
                tot_expand = tot_expand + temp / (fs_now / 1000);
            end                
            
        case 9 % NETEQ_DELAY_LOGGING_SIGNAL_ACCELERATE_INFO
            temp = fread(fid, 1, 'int32');
            if last_decode_k ~= 0
                tot_accelerate = tot_accelerate + temp / (fs_now / 1000);
            end                

        case 10 % NETEQ_DELAY_LOGGING_SIGNAL_PREEMPTIVE_INFO
            temp = fread(fid, 1, 'int32');
            if last_decode_k ~= 0
                tot_preemptive = tot_preemptive + temp / (fs_now / 1000);
            end                
            
        case 11 % NETEQ_DELAY_LOGGING_SIGNAL_OPTBUF
            optbuf(last_decode_k) = fread(fid, 1, 'int32');
            
        case 12 % NETEQ_DELAY_LOGGING_SIGNAL_DECODE_ONE_DESC
            last_decode_ts = fread(fid, 1, 'uint32');
            k = ts_ix - 1;
            
            while (k >= 1) && (rtpts(k) ~= last_decode_ts)
                % TODO(hlundin): use matlab vector search instead?
                k = k - 1;
            end
            
            if k < 1
                % packet not received yet
                k = ts_ix;
                rtpts(ts_ix) = last_decode_ts;
                late_packets = late_packets + 1;
            end
            
            decode_t(k) = clock;
            playout_delay(k) = fread(fid, 1, 'uint16') + ...
                5 *  fs_now / 8000; % add overlap length
            last_decode_k = k;
             
    end
    
end


fclose(fid);

outStruct = struct(...
    'ts', rtpts, ...
    'sn', seqno, ...
    'pt', pt,...
    'plen', plen,...
    'arrival', recin_t,...
    'decode', decode_t,...
    'fs', fsvec(:),...
    'fschange_ts', fschange_ts(:),...
    'playout_delay', playout_delay,...
    'tot_expand', tot_expand,...
    'tot_accelerate', tot_accelerate,...
    'tot_preemptive', tot_preemptive,...
    'optbuf', optbuf);
