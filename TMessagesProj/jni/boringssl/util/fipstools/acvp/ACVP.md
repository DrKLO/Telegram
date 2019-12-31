ACVP Client
===========

[ACVP](https://github.com/usnistgov/ACVP) is the next version of NIST's [CAVP](https://github.com/usnistgov/ACVP)—a program for running cryptographic implementations against a set of test vectors. CAVP involved emailing around zip files of somewhat-INI-like test vectors where no two files had quite the same format. ACVP is supposed to replace that with a) TLS connections rather than email and b) JSON rather than bespoke formats.

The tool in this directory can speak to ACVP servers and run the resulting test vectors through a candidate FIPS module by lowering the tests to a much simpler protocol. It also provides an interface for manipulating the ACVP database which includes lists of modules, vendors, contacts, operating environments etc.

## Configuration

Configuration is done via a `config.json` file in the current working directory. Here's a template:

```
{
        "ACVPServer": "https://demo.acvts.nist.gov/",
        "CertPEMFile": "certificate_from_nist.pem",
        "PrivateKeyDERFile": "your_private_key.key",
        "TOTPSecret": "<base64 from NIST goes here>",
        "SessionTokensCache": "~/.cache/acvp-session-tokens",
        "LogFile": "log"
}
```

NIST's ACVP servers use both TLS client certificates and TOTP for authentication. When registering with NIST, they'll sign a CSR and return a certificate in PEM format, which is pointed to be `CertPEMFile`. The corresponding PKCS#1, DER-encoded private key is expected in `PrivateKeyDERFile`. Lastly, NIST will provide a file that contains the base64-encoded TOTP seed, which must be pasted in as the value of `TOTPSecret`.

NIST's ACVP server provides special access tokens for each test session and test sessions can _only_ be accessed via those tokens. The reasoning behind this is unclear but this client can, optionally, keep records of these access tokens in the directory named by `SessionTokensCache`. If that directory name begins with `~/` then that prefix will be replaced with the value of `$HOME`.

Lastly, a log of all HTTP traffic will be written to the file named by `LogFile`, if provided. This is useful for debugging.

## Interactive Use

ACVP provides a fairly complex interface to a database of several types of objects. A rough UI is provided for this which is triggered when the client is invoked with no command-line arguments.

The simplest objects in ACVP are request objects. These record the status of requested changes to the database and, in practice, changes to the NIST demo database never succeed. The set of pending requests for the current user can be enumerated just by evaluating the `requests` object:

```
> requests
[
  {
    "url": "/acvp/v1/requests/374",
    "status": "processing"
  },
  {
    "url": "/acvp/v1/requests/218",
    "status": "processing"
  }
]
```

A specific request can be evaluated by using indexing syntax:

```
> requests[374]
{
  "url": "/acvp/v1/requests/374",
  "status": "processing"
}
```

The list of vendors provides a more complex example. Since there are large number of duplicates in NIST's database, there are more than 10 000 vendor objects and enumerating them all takes a long time. Thus evaluating the `vendors` object doesn't do that:

```
> vendors
[object set vendors]
```

It is still possible to use indexing syntax to read a specific vendor object if you know the ID:

```
> vendors[1234]
{
  "url": "/acvp/v1/vendors/1234",
  "name": "Apple Inc.",
  "website": "www.apple.com",
  "contactsUrl": "/acvp/v1/vendors/1234/contacts",
  "addresses": [
    {
      "url": "/acvp/v1/vendors/1234/addresses/1234",
      "street1": "1 Infinite Loop",
      "locality": "Cupertino",
      "region": "CA",
      "country": "USA",
      "postalCode": "95014"
    }
  ]
}
```

Finding a vendor when the ID is not known requires searching and the ACVP spec [documents](http://usnistgov.github.io/ACVP/artifacts/draft-fussell-acvp-spec-00.html#rfc.section.11.8.1), for each object type, what values and what relations can be searched on. This is reflected in a variant of the indexing syntax:

```
> vendors[where name contains "Google LLC"]
[
  {
    "url": "/acvp/v1/vendors/11136",
    "name": "Google LLC",
    "website": "www.google.com",
    "contactsUrl": "/acvp/v1/vendors/11136/contacts",
    "addresses": [
      {
        "url": "/acvp/v1/vendors/11136/addresses/11136",
        "street1": "1600 Amphitheatre Parkway",
        "locality": "Mountain View",
        "region": "CA",
        "country": "USA",
        "postalCode": "94043"
      }
    ]
  },
  {
    "url": "/acvp/v1/vendors/11137",
    "name": "Google LLC",
    "website": "www.google.com",
    "contactsUrl": "/acvp/v1/vendors/11137/contacts",
    "addresses": [
      {
        "url": "/acvp/v1/vendors/11137/addresses/11137",
        "street1": "1600 Amphitheatre Parkway",
        "locality": "Mountain View",
        "region": "CA",
        "country": "USA",
        "postalCode": "94043"
      }
    ]
  }
]
```

In general, `&&` and `||` can be used as in C and the relationships are `==`, `!=`, `contains`, `startsWith`, and `endsWith`. Only values and relations listed in the ACVP spec for a given object can be used.

More complex interaction remains to be fleshed out. However, it is generally possible to create new objects by evaluating, for example, `vendors.new()`. That will invoke `$EDITOR` to edit the JSON to be submitted. (For now, however, no helpful templates are provided.)

The current list of objects is:

* `requests`
* `vendors`
* `persons`
* `modules`
* `oes` (operating environments)
* `deps`
* `algos`
* `sessions`

## Running test sessions

Handling of test sessions (in non-interactive mode) is split into a “front” part, which talks to the ACVP server, and a “middle” part, which runs the actual test vectors. The middle part receives the raw JSON of the vector sets and returns the response. It also knows the set of algorithms that it supports and their respective parameters. For the moment, the only middle part provided is called `subprocess` which lowers the ACVP tests to simple binary protocol and talks to a FIPS module in a separate process to run the cryptographic algorithms.

For development purposes, this code can be exercised by passing, say, `-run SHA2-256` to the client.

### The subprocess protocol

The lowering of ACVP to a simpler protocol might be useful for other projects so the protocol is described here. The C++ implementation for BoringSSL is in the `modulewrapper` directory.

The protocol follows a strict request–response model over stdin/stdout: the subprocess only speaks in response to a request and there is exactly one response for every request. Conceptually requests consist of one or more byte strings and responses consist of zero or more byte strings.

On the wire, a request involves sending the number of byte strings, then the length of each byte string in order, then the contents of each byte string. All numbers are little-endian and 32-bit. The first byte string is mandatory and is the name of the command to perform. A response has the same format except that there may be zero byte strings and the first byte string has no special semantics.

All implementations must support the `getConfig` command which takes no arguments and returns a single byte string which is a JSON blob of ACVP algorithm configuration. This blob describes all the algorithms and capabilities that the module supports and is an array of JSON objects suitable for including as the `algorithms` value when [creating an ACVP vector set](http://usnistgov.github.io/ACVP/artifacts/draft-fussell-acvp-spec-00.html#rfc.section.11.15.2.1).

Each supported algorithm will have its own commands that the module must implement. So far, only hash functions are supported and the commands take a byte string to hash and return a single byte string of the resulting digest. The commands are named after the ACVP algorithm names, i.e. `SHA-1`, `SHA2-224`, `SHA2-256`, `SHA2-384`, and `SHA2-512`.
