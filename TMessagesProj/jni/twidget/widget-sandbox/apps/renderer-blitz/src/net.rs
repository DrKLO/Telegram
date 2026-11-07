//! The renderer's only "network": `data:` URLs decoded in process. Anything
//! else is refused and reported, which is the renderer-side half of invariant 4
//! (the validator already strips every other URL before an op gets here).

use blitz_traits::net::{Bytes, NetHandler, NetProvider, Request};
use std::sync::Mutex;

pub struct DataOnlyNet {
    /// Refused URLs (scheme + first 80 chars), drained into `url-blocked` diagnostics.
    pub blocked: Mutex<Vec<String>>,
}

impl DataOnlyNet {
    pub fn new() -> Self {
        Self { blocked: Mutex::new(Vec::new()) }
    }
}

impl NetProvider for DataOnlyNet {
    fn fetch(&self, _doc_id: usize, request: Request, handler: Box<dyn NetHandler>) {
        let url = request.url.as_str().to_string();
        if request.url.scheme() == "data" {
            if let Ok(data_url) = data_url::DataUrl::process(&url) {
                if let Ok((body, _fragment)) = data_url.decode_to_vec() {
                    handler.bytes(url, Bytes::from(body));
                }
            }
            return;
        }
        let mut blocked = self.blocked.lock().unwrap();
        if blocked.len() < 50 {
            let mut shown: String = url.chars().take(80).collect();
            if url.len() > 80 {
                shown.push('…');
            }
            blocked.push(shown);
        }
    }
}
