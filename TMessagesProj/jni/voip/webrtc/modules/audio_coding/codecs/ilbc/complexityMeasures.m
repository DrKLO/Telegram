% % Copyright(c) 2011 The WebRTC project authors.All Rights Reserved.%
        % Use of this source code is governed by a BSD
    -
    style license % that can be found in the LICENSE file in the root of the source
        % tree.An additional intellectual property rights grant can be found
        % in the file PATENTS.All contributing project authors may
        % be found in the AUTHORS file in the root of the source tree.%

        clear;
pack;
%
% Enter the path to YOUR executable and remember to define the perprocessor
% variable PRINT_MIPS te get the instructions printed to the screen.
%
command = '!iLBCtest.exe 30 speechAndBGnoise.pcm out1.bit out1.pcm tlm10_30ms.dat';
cout=' > st.txt';   %saves to matlab variable 'st'
eval(strcat(command,cout));
if(length(cout)>3)
    load st.txt
else
    disp('No cout file to load')
end

% initialize vector to zero
index = find(st(1:end,1)==-1);
indexnonzero = find(st(1:end,1)>0);
frames = length(index)-indexnonzero(1)+1;
start = indexnonzero(1) - 1;
functionOrder=max(st(:,2));
new=zeros(frames,functionOrder);

for i = 1:frames,
    for j = index(start-1+i)+1:(index(start+i)-1),
        new(i,st(j,2)) = new(i,st(j,2)) + st(j,1);
    end
end

result=zeros(functionOrder,3);
for i=1:functionOrder
    nonzeroelements = find(new(1:end,i)>0);
    result(i,1)=i;
    
    % Compute each function's mean complexity
    % result(i,2)=(sum(new(nonzeroelements,i))/(length(nonzeroelements)*0.03))/1000000;
    
    % Compute each function's maximum complexity in encoding
    % and decoding respectively and then add it together:
    % result(i,3)=(max(new(1:end,i))/0.03)/1000000;
    result(i,3)=(max(new(1:size(new,1)/2,i))/0.03)/1000000 + (max(new(size(new,1)/2+1:end,i))/0.03)/1000000;
end

result

% Compute maximum complexity for a single frame (enc/dec separately and together)
maxEncComplexityInAFrame = (max(sum(new(1:size(new,1)/2,:),2))/0.03)/1000000
maxDecComplexityInAFrame = (max(sum(new(size(new,1)/2+1:end,:),2))/0.03)/1000000
totalComplexity = maxEncComplexityInAFrame + maxDecComplexityInAFrame
