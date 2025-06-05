%
%  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
%
%  Use of this source code is governed by a BSD-style license
%  that can be found in the LICENSE file in the root of the source
%  tree. An additional intellectual property rights grant can be found
%  in the file PATENTS.  All contributing project authors may
%  be found in the AUTHORS file in the root of the source tree.
%

function [delay_struct, delayvalues] = plot_neteq_delay(delayfile, varargin)

% InfoStruct = plot_neteq_delay(delayfile)
% InfoStruct = plot_neteq_delay(delayfile, 'skipdelay', skip_seconds)
%
% Henrik Lundin, 2006-11-17
% Henrik Lundin, 2011-05-17
%

try
    s = parse_delay_file(delayfile);
catch
    error(lasterr);
end

delayskip=0;
noplot=0;
arg_ptr=1;
delaypoints=[];

s.sn=unwrap_seqno(s.sn);

while arg_ptr+1 <= nargin
    switch lower(varargin{arg_ptr})
    case {'skipdelay', 'delayskip'}
        % skip a number of seconds in the beginning when calculating delays
        delayskip = varargin{arg_ptr+1};
        arg_ptr = arg_ptr + 2;
    case 'noplot'
        noplot=1;
        arg_ptr = arg_ptr + 1;
    case {'get_delay', 'getdelay'}
        % return a vector of delay values for the points in the given vector
        delaypoints = varargin{arg_ptr+1};
        arg_ptr = arg_ptr + 2;
    otherwise
        warning('Unknown switch %s\n', varargin{arg_ptr});
        arg_ptr = arg_ptr + 1;
    end
end

% find lost frames that were covered by one-descriptor decoding
one_desc_ix=find(isnan(s.arrival));
for k=1:length(one_desc_ix)
    ix=find(s.ts==max(s.ts(s.ts(one_desc_ix(k))>s.ts)));
    s.sn(one_desc_ix(k))=s.sn(ix)+1;
    s.pt(one_desc_ix(k))=s.pt(ix);
    s.arrival(one_desc_ix(k))=s.arrival(ix)+s.decode(one_desc_ix(k))-s.decode(ix);
end

% remove duplicate received frames that were never decoded (RED codec)
if length(unique(s.ts(isfinite(s.ts)))) < length(s.ts(isfinite(s.ts)))
    ix=find(isfinite(s.decode));
    s.sn=s.sn(ix);
    s.ts=s.ts(ix);
    s.arrival=s.arrival(ix);
    s.playout_delay=s.playout_delay(ix);
    s.pt=s.pt(ix);
    s.optbuf=s.optbuf(ix);
    plen=plen(ix);
    s.decode=s.decode(ix);
end

% find non-unique sequence numbers
[~,un_ix]=unique(s.sn);
nonun_ix=setdiff(1:length(s.sn),un_ix);
if ~isempty(nonun_ix)
    warning('RTP sequence numbers are in error');
end
            
% sort vectors
[s.sn,sort_ix]=sort(s.sn);
s.ts=s.ts(sort_ix);
s.arrival=s.arrival(sort_ix);
s.decode=s.decode(sort_ix);
s.playout_delay=s.playout_delay(sort_ix);
s.pt=s.pt(sort_ix);

send_t=s.ts-s.ts(1);
if length(s.fs)<1
    warning('No info about sample rate found in file. Using default 8000.');
    s.fs(1)=8000;
    s.fschange_ts(1)=min(s.ts);
elseif s.fschange_ts(1)>min(s.ts)
    s.fschange_ts(1)=min(s.ts);
end

end_ix=length(send_t);
for k=length(s.fs):-1:1
    start_ix=find(s.ts==s.fschange_ts(k));
    send_t(start_ix:end_ix)=send_t(start_ix:end_ix)/s.fs(k)*1000;
    s.playout_delay(start_ix:end_ix)=s.playout_delay(start_ix:end_ix)/s.fs(k)*1000;
    s.optbuf(start_ix:end_ix)=s.optbuf(start_ix:end_ix)/s.fs(k)*1000;
    end_ix=start_ix-1;
end

tot_time=max(send_t)-min(send_t);

seq_ix=s.sn-min(s.sn)+1;
send_t=send_t+max(min(s.arrival-send_t),0);

plot_send_t=nan*ones(max(seq_ix),1);
plot_send_t(seq_ix)=send_t;
plot_nw_delay=nan*ones(max(seq_ix),1);
plot_nw_delay(seq_ix)=s.arrival-send_t;

cng_ix=find(s.pt~=13); % find those packets that are not CNG/SID
    
if noplot==0
    h=plot(plot_send_t/1000,plot_nw_delay);
    set(h,'color',0.75*[1 1 1]);
    hold on
    if any(s.optbuf~=0)
        peak_ix=find(s.optbuf(cng_ix)<0); % peak mode is labeled with negative values
        no_peak_ix=find(s.optbuf(cng_ix)>0); %setdiff(1:length(cng_ix),peak_ix);
        h1=plot(send_t(cng_ix(peak_ix))/1000,...
            s.arrival(cng_ix(peak_ix))+abs(s.optbuf(cng_ix(peak_ix)))-send_t(cng_ix(peak_ix)),...
            'r.');
        h2=plot(send_t(cng_ix(no_peak_ix))/1000,...
            s.arrival(cng_ix(no_peak_ix))+abs(s.optbuf(cng_ix(no_peak_ix)))-send_t(cng_ix(no_peak_ix)),...
            'g.');
        set([h1, h2],'markersize',1)
    end
    %h=plot(send_t(seq_ix)/1000,s.decode+s.playout_delay-send_t(seq_ix));
    h=plot(send_t(cng_ix)/1000,s.decode(cng_ix)+s.playout_delay(cng_ix)-send_t(cng_ix));
    set(h,'linew',1.5);
    hold off
    ax1=axis;
    axis tight
    ax2=axis;
    axis([ax2(1:3) ax1(4)])
end


% calculate delays and other parameters

delayskip_ix = find(send_t-send_t(1)>=delayskip*1000, 1 );

use_ix = intersect(cng_ix,... % use those that are not CNG/SID frames...
    intersect(find(isfinite(s.decode)),... % ... that did arrive ...
    (delayskip_ix:length(s.decode))')); % ... and are sent after delayskip seconds

mean_delay = mean(s.decode(use_ix)+s.playout_delay(use_ix)-send_t(use_ix));
neteq_delay = mean(s.decode(use_ix)+s.playout_delay(use_ix)-s.arrival(use_ix));

Npack=max(s.sn(delayskip_ix:end))-min(s.sn(delayskip_ix:end))+1;
nw_lossrate=(Npack-length(s.sn(delayskip_ix:end)))/Npack;
neteq_lossrate=(length(s.sn(delayskip_ix:end))-length(use_ix))/Npack;

delay_struct=struct('mean_delay',mean_delay,'neteq_delay',neteq_delay,...
    'nw_lossrate',nw_lossrate,'neteq_lossrate',neteq_lossrate,...
    'tot_expand',round(s.tot_expand),'tot_accelerate',round(s.tot_accelerate),...
    'tot_preemptive',round(s.tot_preemptive),'tot_time',tot_time,...
    'filename',delayfile,'units','ms','fs',unique(s.fs));
    
if not(isempty(delaypoints))
    delayvalues=interp1(send_t(cng_ix),...
        s.decode(cng_ix)+s.playout_delay(cng_ix)-send_t(cng_ix),...
        delaypoints,'nearest',NaN);
else
    delayvalues=[];
end



% SUBFUNCTIONS %

function y=unwrap_seqno(x)

jumps=find(abs((diff(x)-1))>65000);

while ~isempty(jumps)
    n=jumps(1);
    if x(n+1)-x(n) < 0
        % negative jump
        x(n+1:end)=x(n+1:end)+65536;
    else
        % positive jump
        x(n+1:end)=x(n+1:end)-65536;
    end
    
    jumps=find(abs((diff(x(n+1:end))-1))>65000);
end

y=x;

return;
