"""Additional seam check for smooth, opaque parts of fitted split images."""
import numpy as np
from scipy.ndimage import uniform_filter1d, maximum_filter, minimum_filter

def passes_seam(image,baseline,original,axis,cut):
 # Look only for coherent seam errors inside smooth, opaque source regions.
 src=original[:,:,:3].astype(np.float32)
 spread=maximum_filter(src,size=(5,5,1))-minimum_filter(src,size=(5,5,1))
 smooth=np.all(spread<16,axis=2)&(original[:,:,3]>=250)&(baseline[:,:,3]>=250)
 mask=(smooth[cut]&smooth[cut-1])if axis==1 else(smooth[:,cut]&smooth[:,cut-1])
 if not np.any(mask):return True
 for bg in [0.,255.]:
  def sample(a):
   v=a[:,:,:3].astype(np.float32)*(a[:,:,3:]/255.)+bg*(1-a[:,:,3:]/255.)
   return v[cut]-v[cut-1]if axis==1 else v[:,cut]-v[:,cut-1]
  source=sample(original);a=np.abs(uniform_filter1d(sample(image)-source,5,axis=0));b=np.abs(uniform_filter1d(sample(baseline)-source,5,axis=0))
  if np.any(a[mask]>np.maximum(b[mask]*2.,4.)):return False
 return True

