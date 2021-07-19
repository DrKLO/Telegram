/* Copyright (c) 2016, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <algorithm>
#include <functional>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include <openssl/bio.h>
#include <openssl/bytestring.h>
#include <openssl/crypto.h>
#include <openssl/curve25519.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/pool.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>

#include "../internal.h"
#include "../test/test_util.h"
#include "../x509v3/internal.h"


std::string GetTestData(const char *path);

static const char kCrossSigningRootPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICcTCCAdqgAwIBAgIIagJHiPvE0MowDQYJKoZIhvcNAQELBQAwPDEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxHjAcBgNVBAMTFUNyb3NzLXNpZ25pbmcgUm9v\n"
    "dCBDQTAgFw0xNTAxMDEwMDAwMDBaGA8yMTAwMDEwMTAwMDAwMFowPDEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxHjAcBgNVBAMTFUNyb3NzLXNpZ25pbmcgUm9v\n"
    "dCBDQTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAwo3qFvSB9Zmlbpzn9wJp\n"
    "ikI75Rxkatez8VkLqyxbOhPYl2Haz8F5p1gDG96dCI6jcLGgu3AKT9uhEQyyUko5\n"
    "EKYasazSeA9CQrdyhPg0mkTYVETnPM1W/ebid1YtqQbq1CMWlq2aTDoSGAReGFKP\n"
    "RTdXAbuAXzpCfi/d8LqV13UCAwEAAaN6MHgwDgYDVR0PAQH/BAQDAgIEMB0GA1Ud\n"
    "JQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAPBgNVHRMBAf8EBTADAQH/MBkGA1Ud\n"
    "DgQSBBBHKHC7V3Z/3oLvEZx0RZRwMBsGA1UdIwQUMBKAEEcocLtXdn/egu8RnHRF\n"
    "lHAwDQYJKoZIhvcNAQELBQADgYEAnglibsy6mGtpIXivtlcz4zIEnHw/lNW+r/eC\n"
    "CY7evZTmOoOuC/x9SS3MF9vawt1HFUummWM6ZgErqVBOXIB4//ykrcCgf5ZbF5Hr\n"
    "+3EFprKhBqYiXdD8hpBkrBoXwn85LPYWNd2TceCrx0YtLIprE2R5MB2RIq8y4Jk3\n"
    "YFXvkME=\n"
    "-----END CERTIFICATE-----\n";

static const char kRootCAPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICVTCCAb6gAwIBAgIIAj5CwoHlWuYwDQYJKoZIhvcNAQELBQAwLjEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxEDAOBgNVBAMTB1Jvb3QgQ0EwIBcNMTUwMTAx\n"
    "MDAwMDAwWhgPMjEwMDAxMDEwMDAwMDBaMC4xGjAYBgNVBAoTEUJvcmluZ1NTTCBU\n"
    "RVNUSU5HMRAwDgYDVQQDEwdSb290IENBMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCB\n"
    "iQKBgQDpDn8RDOZa5oaDcPZRBy4CeBH1siSSOO4mYgLHlPE+oXdqwI/VImi2XeJM\n"
    "2uCFETXCknJJjYG0iJdrt/yyRFvZTQZw+QzGj+mz36NqhGxDWb6dstB2m8PX+plZ\n"
    "w7jl81MDvUnWs8yiQ/6twgu5AbhWKZQDJKcNKCEpqa6UW0r5nwIDAQABo3oweDAO\n"
    "BgNVHQ8BAf8EBAMCAgQwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMA8G\n"
    "A1UdEwEB/wQFMAMBAf8wGQYDVR0OBBIEEEA31wH7QC+4HH5UBCeMWQEwGwYDVR0j\n"
    "BBQwEoAQQDfXAftAL7gcflQEJ4xZATANBgkqhkiG9w0BAQsFAAOBgQDXylEK77Za\n"
    "kKeY6ZerrScWyZhrjIGtHFu09qVpdJEzrk87k2G7iHHR9CAvSofCgEExKtWNS9dN\n"
    "+9WiZp/U48iHLk7qaYXdEuO07No4BYtXn+lkOykE+FUxmA4wvOF1cTd2tdj3MzX2\n"
    "kfGIBAYhzGZWhY3JbhIfTEfY1PNM1pWChQ==\n"
    "-----END CERTIFICATE-----\n";

static const char kRootCrossSignedPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICYzCCAcygAwIBAgIIAj5CwoHlWuYwDQYJKoZIhvcNAQELBQAwPDEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxHjAcBgNVBAMTFUNyb3NzLXNpZ25pbmcgUm9v\n"
    "dCBDQTAgFw0xNTAxMDEwMDAwMDBaGA8yMTAwMDEwMTAwMDAwMFowLjEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxEDAOBgNVBAMTB1Jvb3QgQ0EwgZ8wDQYJKoZI\n"
    "hvcNAQEBBQADgY0AMIGJAoGBAOkOfxEM5lrmhoNw9lEHLgJ4EfWyJJI47iZiAseU\n"
    "8T6hd2rAj9UiaLZd4kza4IURNcKSckmNgbSIl2u3/LJEW9lNBnD5DMaP6bPfo2qE\n"
    "bENZvp2y0Habw9f6mVnDuOXzUwO9SdazzKJD/q3CC7kBuFYplAMkpw0oISmprpRb\n"
    "SvmfAgMBAAGjejB4MA4GA1UdDwEB/wQEAwICBDAdBgNVHSUEFjAUBggrBgEFBQcD\n"
    "AQYIKwYBBQUHAwIwDwYDVR0TAQH/BAUwAwEB/zAZBgNVHQ4EEgQQQDfXAftAL7gc\n"
    "flQEJ4xZATAbBgNVHSMEFDASgBBHKHC7V3Z/3oLvEZx0RZRwMA0GCSqGSIb3DQEB\n"
    "CwUAA4GBAErTxYJ0en9HVRHAAr5OO5wuk5Iq3VMc79TMyQLCXVL8YH8Uk7KEwv+q\n"
    "9MEKZv2eR/Vfm4HlXlUuIqfgUXbwrAYC/YVVX86Wnbpy/jc73NYVCq8FEZeO+0XU\n"
    "90SWAPDdp+iL7aZdimnMtG1qlM1edmz8AKbrhN/R3IbA2CL0nCWV\n"
    "-----END CERTIFICATE-----\n";

static const char kIntermediatePEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICXjCCAcegAwIBAgIJAKJMH+7rscPcMA0GCSqGSIb3DQEBCwUAMC4xGjAYBgNV\n"
    "BAoTEUJvcmluZ1NTTCBURVNUSU5HMRAwDgYDVQQDEwdSb290IENBMCAXDTE1MDEw\n"
    "MTAwMDAwMFoYDzIxMDAwMTAxMDAwMDAwWjA2MRowGAYDVQQKExFCb3JpbmdTU0wg\n"
    "VEVTVElORzEYMBYGA1UEAxMPSW50ZXJtZWRpYXRlIENBMIGfMA0GCSqGSIb3DQEB\n"
    "AQUAA4GNADCBiQKBgQC7YtI0l8ocTYJ0gKyXTtPL4iMJCNY4OcxXl48jkncVG1Hl\n"
    "blicgNUa1r9m9YFtVkxvBinb8dXiUpEGhVg4awRPDcatlsBSEBuJkiZGYbRcAmSu\n"
    "CmZYnf6u3aYQ18SU8WqVERPpE4cwVVs+6kwlzRw0+XDoZAczu8ZezVhCUc6NbQID\n"
    "AQABo3oweDAOBgNVHQ8BAf8EBAMCAgQwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\n"
    "AQUFBwMCMA8GA1UdEwEB/wQFMAMBAf8wGQYDVR0OBBIEEIwaaKi1dttdV3sfjRSy\n"
    "BqMwGwYDVR0jBBQwEoAQQDfXAftAL7gcflQEJ4xZATANBgkqhkiG9w0BAQsFAAOB\n"
    "gQCvnolNWEHuQS8PFVVyuLR+FKBeUUdrVbSfHSzTqNAqQGp0C9fk5oCzDq6ZgTfY\n"
    "ESXM4cJhb3IAnW0UM0NFsYSKQJ50JZL2L3z5ZLQhHdbs4RmODGoC40BVdnJ4/qgB\n"
    "aGSh09eQRvAVmbVCviDK2ipkWNegdyI19jFfNP5uIkGlYg==\n"
    "-----END CERTIFICATE-----\n";

static const char kIntermediateSelfSignedPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICZjCCAc+gAwIBAgIJAKJMH+7rscPcMA0GCSqGSIb3DQEBCwUAMDYxGjAYBgNV\n"
    "BAoTEUJvcmluZ1NTTCBURVNUSU5HMRgwFgYDVQQDEw9JbnRlcm1lZGlhdGUgQ0Ew\n"
    "IBcNMTUwMTAxMDAwMDAwWhgPMjEwMDAxMDEwMDAwMDBaMDYxGjAYBgNVBAoTEUJv\n"
    "cmluZ1NTTCBURVNUSU5HMRgwFgYDVQQDEw9JbnRlcm1lZGlhdGUgQ0EwgZ8wDQYJ\n"
    "KoZIhvcNAQEBBQADgY0AMIGJAoGBALti0jSXyhxNgnSArJdO08viIwkI1jg5zFeX\n"
    "jyOSdxUbUeVuWJyA1RrWv2b1gW1WTG8GKdvx1eJSkQaFWDhrBE8Nxq2WwFIQG4mS\n"
    "JkZhtFwCZK4KZlid/q7dphDXxJTxapURE+kThzBVWz7qTCXNHDT5cOhkBzO7xl7N\n"
    "WEJRzo1tAgMBAAGjejB4MA4GA1UdDwEB/wQEAwICBDAdBgNVHSUEFjAUBggrBgEF\n"
    "BQcDAQYIKwYBBQUHAwIwDwYDVR0TAQH/BAUwAwEB/zAZBgNVHQ4EEgQQjBpoqLV2\n"
    "211Xex+NFLIGozAbBgNVHSMEFDASgBCMGmiotXbbXVd7H40UsgajMA0GCSqGSIb3\n"
    "DQEBCwUAA4GBALcccSrAQ0/EqQBsx0ZDTUydHXXNP2DrUkpUKmAXIe8McqIVSlkT\n"
    "6H4xz7z8VRKBo9j+drjjtCw2i0CQc8aOLxRb5WJ8eVLnaW2XRlUqAzhF0CrulfVI\n"
    "E4Vs6ZLU+fra1WAuIj6qFiigRja+3YkZArG8tMA9vtlhTX/g7YBZIkqH\n"
    "-----END CERTIFICATE-----\n";

static const char kLeafPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICXjCCAcegAwIBAgIIWjO48ufpunYwDQYJKoZIhvcNAQELBQAwNjEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxGDAWBgNVBAMTD0ludGVybWVkaWF0ZSBDQTAg\n"
    "Fw0xNTAxMDEwMDAwMDBaGA8yMTAwMDEwMTAwMDAwMFowMjEaMBgGA1UEChMRQm9y\n"
    "aW5nU1NMIFRFU1RJTkcxFDASBgNVBAMTC2V4YW1wbGUuY29tMIGfMA0GCSqGSIb3\n"
    "DQEBAQUAA4GNADCBiQKBgQDD0U0ZYgqShJ7oOjsyNKyVXEHqeafmk/bAoPqY/h1c\n"
    "oPw2E8KmeqiUSoTPjG5IXSblOxcqpbAXgnjPzo8DI3GNMhAf8SYNYsoH7gc7Uy7j\n"
    "5x8bUrisGnuTHqkqH6d4/e7ETJ7i3CpR8bvK16DggEvQTudLipz8FBHtYhFakfdh\n"
    "TwIDAQABo3cwdTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEG\n"
    "CCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwGQYDVR0OBBIEEKN5pvbur7mlXjeMEYA0\n"
    "4nUwGwYDVR0jBBQwEoAQjBpoqLV2211Xex+NFLIGozANBgkqhkiG9w0BAQsFAAOB\n"
    "gQBj/p+JChp//LnXWC1k121LM/ii7hFzQzMrt70bny406SGz9jAjaPOX4S3gt38y\n"
    "rhjpPukBlSzgQXFg66y6q5qp1nQTD1Cw6NkKBe9WuBlY3iYfmsf7WT8nhlT1CttU\n"
    "xNCwyMX9mtdXdQicOfNjIGUCD5OLV5PgHFPRKiHHioBAhg==\n"
    "-----END CERTIFICATE-----\n";

static const char kLeafNoKeyUsagePEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICNTCCAZ6gAwIBAgIJAIFQGaLQ0G2mMA0GCSqGSIb3DQEBCwUAMDYxGjAYBgNV\n"
    "BAoTEUJvcmluZ1NTTCBURVNUSU5HMRgwFgYDVQQDEw9JbnRlcm1lZGlhdGUgQ0Ew\n"
    "IBcNMTUwMTAxMDAwMDAwWhgPMjEwMDAxMDEwMDAwMDBaMDcxGjAYBgNVBAoTEUJv\n"
    "cmluZ1NTTCBURVNUSU5HMRkwFwYDVQQDExBldmlsLmV4YW1wbGUuY29tMIGfMA0G\n"
    "CSqGSIb3DQEBAQUAA4GNADCBiQKBgQDOKoZe75NPz77EOaMMl4/0s3PyQw++zJvp\n"
    "ejHAxZiTPCJgMbEHLrSzNoHdopg+CLUH5bE4wTXM8w9Inv5P8OAFJt7gJuPUunmk\n"
    "j+NoU3QfzOR6BroePcz1vXX9jyVHRs087M/sLqWRHu9IR+/A+UTcBaWaFiDVUxtJ\n"
    "YOwFMwjNPQIDAQABo0gwRjAMBgNVHRMBAf8EAjAAMBkGA1UdDgQSBBBJfLEUWHq1\n"
    "27rZ1AVx2J5GMBsGA1UdIwQUMBKAEIwaaKi1dttdV3sfjRSyBqMwDQYJKoZIhvcN\n"
    "AQELBQADgYEALVKN2Y3LZJOtu6SxFIYKxbLaXhTGTdIjxipZhmbBRDFjbZjZZOTe\n"
    "6Oo+VDNPYco4rBexK7umYXJyfTqoY0E8dbiImhTcGTEj7OAB3DbBomgU1AYe+t2D\n"
    "uwBqh4Y3Eto+Zn4pMVsxGEfUpjzjZDel7bN1/oU/9KWPpDfywfUmjgk=\n"
    "-----END CERTIFICATE-----\n";

static const char kForgeryPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICZzCCAdCgAwIBAgIIdTlMzQoKkeMwDQYJKoZIhvcNAQELBQAwNzEaMBgGA1UE\n"
    "ChMRQm9yaW5nU1NMIFRFU1RJTkcxGTAXBgNVBAMTEGV2aWwuZXhhbXBsZS5jb20w\n"
    "IBcNMTUwMTAxMDAwMDAwWhgPMjEwMDAxMDEwMDAwMDBaMDoxGjAYBgNVBAoTEUJv\n"
    "cmluZ1NTTCBURVNUSU5HMRwwGgYDVQQDExNmb3JnZXJ5LmV4YW1wbGUuY29tMIGf\n"
    "MA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDADTwruBQZGb7Ay6s9HiYv5d1lwtEy\n"
    "xQdA2Sy8Rn8uA20Q4KgqwVY7wzIZ+z5Butrsmwb70gdG1XU+yRaDeE7XVoW6jSpm\n"
    "0sw35/5vJbTcL4THEFbnX0OPZnvpuZDFUkvVtq5kxpDWsVyM24G8EEq7kPih3Sa3\n"
    "OMhXVXF8kso6UQIDAQABo3cwdTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYI\n"
    "KwYBBQUHAwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwGQYDVR0OBBIEEEYJ/WHM\n"
    "8p64erPWIg4/liwwGwYDVR0jBBQwEoAQSXyxFFh6tdu62dQFcdieRjANBgkqhkiG\n"
    "9w0BAQsFAAOBgQA+zH7bHPElWRWJvjxDqRexmYLn+D3Aivs8XgXQJsM94W0EzSUf\n"
    "DSLfRgaQwcb2gg2xpDFoG+W0vc6O651uF23WGt5JaFFJJxqjII05IexfCNhuPmp4\n"
    "4UZAXPttuJXpn74IY1tuouaM06B3vXKZR+/ityKmfJvSwxacmFcK+2ziAg==\n"
    "-----END CERTIFICATE-----\n";

// kExamplePSSCert is an example RSA-PSS self-signed certificate, signed with
// the default hash functions.
static const char kExamplePSSCert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICYjCCAcagAwIBAgIJAI3qUyT6SIfzMBIGCSqGSIb3DQEBCjAFogMCAWowRTEL\n"
    "MAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGEludGVy\n"
    "bmV0IFdpZGdpdHMgUHR5IEx0ZDAeFw0xNDEwMDkxOTA5NTVaFw0xNTEwMDkxOTA5\n"
    "NTVaMEUxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQK\n"
    "DBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwgZ8wDQYJKoZIhvcNAQEBBQADgY0A\n"
    "MIGJAoGBAPi4bIO0vNmoV8CltFl2jFQdeesiUgR+0zfrQf2D+fCmhRU0dXFahKg8\n"
    "0u9aTtPel4rd/7vPCqqGkr64UOTNb4AzMHYTj8p73OxaymPHAyXvqIqDWHYg+hZ3\n"
    "13mSYwFIGth7Z/FSVUlO1m5KXNd6NzYM3t2PROjCpywrta9kS2EHAgMBAAGjUDBO\n"
    "MB0GA1UdDgQWBBTQQfuJQR6nrVrsNF1JEflVgXgfEzAfBgNVHSMEGDAWgBTQQfuJ\n"
    "QR6nrVrsNF1JEflVgXgfEzAMBgNVHRMEBTADAQH/MBIGCSqGSIb3DQEBCjAFogMC\n"
    "AWoDgYEASUy2RZcgNbNQZA0/7F+V1YTLEXwD16bm+iSVnzGwtexmQVEYIZG74K/w\n"
    "xbdZQdTbpNJkp1QPjPfh0zsatw6dmt5QoZ8K8No0DjR9dgf+Wvv5WJvJUIQBoAVN\n"
    "Z0IL+OQFz6+LcTHxD27JJCebrATXZA0wThGTQDm7crL+a+SujBY=\n"
    "-----END CERTIFICATE-----\n";

// kBadPSSCertPEM is a self-signed RSA-PSS certificate with bad parameters.
static const char kBadPSSCertPEM[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIDdjCCAjqgAwIBAgIJANcwZLyfEv7DMD4GCSqGSIb3DQEBCjAxoA0wCwYJYIZI\n"
    "AWUDBAIBoRowGAYJKoZIhvcNAQEIMAsGCWCGSAFlAwQCAaIEAgIA3jAnMSUwIwYD\n"
    "VQQDDBxUZXN0IEludmFsaWQgUFNTIGNlcnRpZmljYXRlMB4XDTE1MTEwNDE2MDIz\n"
    "NVoXDTE1MTIwNDE2MDIzNVowJzElMCMGA1UEAwwcVGVzdCBJbnZhbGlkIFBTUyBj\n"
    "ZXJ0aWZpY2F0ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMTaM7WH\n"
    "qVCAGAIA+zL1KWvvASTrhlq+1ePdO7wsrWX2KiYoTYrJYTnxhLnn0wrHqApt79nL\n"
    "IBG7cfShyZqFHOY/IzlYPMVt+gPo293gw96Fds5JBsjhjkyGnOyr9OUntFqvxDbT\n"
    "IIFU7o9IdxD4edaqjRv+fegVE+B79pDk4s0ujsk6dULtCg9Rst0ucGFo19mr+b7k\n"
    "dbfn8pZ72ZNDJPueVdrUAWw9oll61UcYfk75XdrLk6JlL41GrYHc8KlfXf43gGQq\n"
    "QfrpHkg4Ih2cI6Wt2nhFGAzrlcorzLliQIUJRIhM8h4IgDfpBpaPdVQLqS2pFbXa\n"
    "5eQjqiyJwak2vJ8CAwEAAaNQME4wHQYDVR0OBBYEFCt180N4oGUt5LbzBwQ4Ia+2\n"
    "4V97MB8GA1UdIwQYMBaAFCt180N4oGUt5LbzBwQ4Ia+24V97MAwGA1UdEwQFMAMB\n"
    "Af8wMQYJKoZIhvcNAQEKMCSgDTALBglghkgBZQMEAgGhDTALBgkqhkiG9w0BAQii\n"
    "BAICAN4DggEBAAjBtm90lGxgddjc4Xu/nbXXFHVs2zVcHv/mqOZoQkGB9r/BVgLb\n"
    "xhHrFZ2pHGElbUYPfifdS9ztB73e1d4J+P29o0yBqfd4/wGAc/JA8qgn6AAEO/Xn\n"
    "plhFeTRJQtLZVl75CkHXgUGUd3h+ADvKtcBuW9dSUncaUrgNKR8u/h/2sMG38RWY\n"
    "DzBddC/66YTa3r7KkVUfW7yqRQfELiGKdcm+bjlTEMsvS+EhHup9CzbpoCx2Fx9p\n"
    "NPtFY3yEObQhmL1JyoCRWqBE75GzFPbRaiux5UpEkns+i3trkGssZzsOuVqHNTNZ\n"
    "lC9+9hPHIoc9UMmAQNo1vGIW3NWVoeGbaJ8=\n"
    "-----END CERTIFICATE-----\n";

static const char kRSAKey[] =
    "-----BEGIN RSA PRIVATE KEY-----\n"
    "MIICXgIBAAKBgQDYK8imMuRi/03z0K1Zi0WnvfFHvwlYeyK9Na6XJYaUoIDAtB92\n"
    "kWdGMdAQhLciHnAjkXLI6W15OoV3gA/ElRZ1xUpxTMhjP6PyY5wqT5r6y8FxbiiF\n"
    "KKAnHmUcrgfVW28tQ+0rkLGMryRtrukXOgXBv7gcrmU7G1jC2a7WqmeI8QIDAQAB\n"
    "AoGBAIBy09Fd4DOq/Ijp8HeKuCMKTHqTW1xGHshLQ6jwVV2vWZIn9aIgmDsvkjCe\n"
    "i6ssZvnbjVcwzSoByhjN8ZCf/i15HECWDFFh6gt0P5z0MnChwzZmvatV/FXCT0j+\n"
    "WmGNB/gkehKjGXLLcjTb6dRYVJSCZhVuOLLcbWIV10gggJQBAkEA8S8sGe4ezyyZ\n"
    "m4e9r95g6s43kPqtj5rewTsUxt+2n4eVodD+ZUlCULWVNAFLkYRTBCASlSrm9Xhj\n"
    "QpmWAHJUkQJBAOVzQdFUaewLtdOJoPCtpYoY1zd22eae8TQEmpGOR11L6kbxLQsk\n"
    "aMly/DOnOaa82tqAGTdqDEZgSNmCeKKknmECQAvpnY8GUOVAubGR6c+W90iBuQLj\n"
    "LtFp/9ihd2w/PoDwrHZaoUYVcT4VSfJQog/k7kjE4MYXYWL8eEKg3WTWQNECQQDk\n"
    "104Wi91Umd1PzF0ijd2jXOERJU1wEKe6XLkYYNHWQAe5l4J4MWj9OdxFXAxIuuR/\n"
    "tfDwbqkta4xcux67//khAkEAvvRXLHTaa6VFzTaiiO8SaFsHV3lQyXOtMrBpB5jd\n"
    "moZWgjHvB2W9Ckn7sDqsPB+U2tyX0joDdQEyuiMECDY8oQ==\n"
    "-----END RSA PRIVATE KEY-----\n";

// kCRLTestRoot is a test root certificate. It has private key:
//
//     -----BEGIN RSA PRIVATE KEY-----
//     MIIEpAIBAAKCAQEAo16WiLWZuaymsD8n5SKPmxV1y6jjgr3BS/dUBpbrzd1aeFzN
//     lI8l2jfAnzUyp+I21RQ+nh/MhqjGElkTtK9xMn1Y+S9GMRh+5R/Du0iCb1tCZIPY
//     07Tgrb0KMNWe0v2QKVVruuYSgxIWodBfxlKO64Z8AJ5IbnWpuRqO6rctN9qUoMlT
//     IAB6dL4G0tDJ/PGFWOJYwOMEIX54bly2wgyYJVBKiRRt4f7n8H922qmvPNA9idmX
//     9G1VAtgV6x97XXi7ULORIQvn9lVQF6nTYDBJhyuPB+mLThbLP2o9orxGx7aCtnnB
//     ZUIxUvHNOI0FaSaZH7Fi0xsZ/GkG2HZe7ImPJwIDAQABAoIBAQCJF9MTHfHGkk+/
//     DwCXlA0Wg0e6hBuHl10iNobYkMWIl/xXjOknhYiqOqb181py76472SVC5ERprC+r
//     Lf0PXzqKuA117mnkwT2bYLCL9Skf8WEhoFLQNbVlloF6wYjqXcYgKYKh8HgQbZl4
//     aLg2YQl2NADTNABsUWj/4H2WEelsODVviqfFs725lFg9KHDI8zxAZXLzDt/M9uVL
//     GxJiX12tr0AwaeAFZ1oPM/y+LznM3N3+Ht3jHHw3jZ/u8Z1RdAmdpu3bZ6tbwGBr
//     9edsH5rKkm9aBvMrY7eX5VHqaqyRNFyG152ZOJh4XiiFG7EmgTPCpaHo50Y018Re
//     grVtk+FBAoGBANY3lY+V8ZOwMxSHes+kTnoimHO5Ob7nxrOC71i27x+4HHsYUeAr
//     /zOOghiDIn+oNkuiX5CIOWZKx159Bp65CPpCbTb/fh+HYnSgXFgCw7XptycO7LXM
//     5GwR5jSfpfzBFdYxjxoUzDMFBwTEYRTm0HkUHkH+s+ajjw5wqqbcGLcfAoGBAMM8
//     DKW6Tb66xsf708f0jonAjKYTLZ+WOcwsBEWSFHoY8dUjvW5gqx5acHTEsc5ZTeh4
//     BCFLa+Mn9cuJWVJNs09k7Xb2PNl92HQ4GN2vbdkJhExbkT6oLDHg1hVD0w8KLfz1
//     lTAW6pS+6CdOHMEJpvqx89EgU/1GgIQ1fXYczE75AoGAKeJoXdDFkUjsU+FBhAPu
//     TDcjc80Nm2QaF9NMFR5/lsYa236f06MGnQAKM9zADBHJu/Qdl1brUjLg1HrBppsr
//     RDNkw1IlSOjhuUf5hkPUHGd8Jijm440SRIcjabqla8wdBupdvo2+d2NOQgJbsQiI
//     ToQ+fkzcxAXK3Nnuo/1436UCgYBjLH7UNOZHS8OsVM0I1r8NVKVdu4JCfeJQR8/H
//     s2P5ffBir+wLRMnH+nMDreMQiibcPxMCArkERAlE4jlgaJ38Z62E76KLbLTmnJRt
//     EC9Bv+bXjvAiHvWMRMUbOj/ddPNVez7Uld+FvdBaHwDWQlvzHzBWfBCOKSEhh7Z6
//     qDhUqQKBgQDPMDx2i5rfmQp3imV9xUcCkIRsyYQVf8Eo7NV07IdUy/otmksgn4Zt
//     Lbf3v2dvxOpTNTONWjp2c+iUQo8QxJCZr5Sfb21oQ9Ktcrmc/CY7LeBVDibXwxdM
//     vRG8kBzvslFWh7REzC3u06GSVhyKDfW93kN2cKVwGoahRlhj7oHuZQ==
//     -----END RSA PRIVATE KEY-----
static const char kCRLTestRoot[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIDbzCCAlegAwIBAgIJAODri7v0dDUFMA0GCSqGSIb3DQEBCwUAME4xCzAJBgNV\n"
    "BAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBW\n"
    "aWV3MRIwEAYDVQQKDAlCb3JpbmdTU0wwHhcNMTYwOTI2MTUwNjI2WhcNMjYwOTI0\n"
    "MTUwNjI2WjBOMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQG\n"
    "A1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJQm9yaW5nU1NMMIIBIjANBgkq\n"
    "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo16WiLWZuaymsD8n5SKPmxV1y6jjgr3B\n"
    "S/dUBpbrzd1aeFzNlI8l2jfAnzUyp+I21RQ+nh/MhqjGElkTtK9xMn1Y+S9GMRh+\n"
    "5R/Du0iCb1tCZIPY07Tgrb0KMNWe0v2QKVVruuYSgxIWodBfxlKO64Z8AJ5IbnWp\n"
    "uRqO6rctN9qUoMlTIAB6dL4G0tDJ/PGFWOJYwOMEIX54bly2wgyYJVBKiRRt4f7n\n"
    "8H922qmvPNA9idmX9G1VAtgV6x97XXi7ULORIQvn9lVQF6nTYDBJhyuPB+mLThbL\n"
    "P2o9orxGx7aCtnnBZUIxUvHNOI0FaSaZH7Fi0xsZ/GkG2HZe7ImPJwIDAQABo1Aw\n"
    "TjAdBgNVHQ4EFgQUWPt3N5cZ/CRvubbrkqfBnAqhq94wHwYDVR0jBBgwFoAUWPt3\n"
    "N5cZ/CRvubbrkqfBnAqhq94wDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOC\n"
    "AQEAORu6M0MOwXy+3VEBwNilfTxyqDfruQsc1jA4PT8Oe8zora1WxE1JB4q2FJOz\n"
    "EAuM3H/NXvEnBuN+ITvKZAJUfm4NKX97qmjMJwLKWe1gVv+VQTr63aR7mgWJReQN\n"
    "XdMztlVeZs2dppV6uEg3ia1X0G7LARxGpA9ETbMyCpb39XxlYuTClcbA5ftDN99B\n"
    "3Xg9KNdd++Ew22O3HWRDvdDpTO/JkzQfzi3sYwUtzMEonENhczJhGf7bQMmvL/w5\n"
    "24Wxj4Z7KzzWIHsNqE/RIs6RV3fcW61j/mRgW2XyoWnMVeBzvcJr9NXp4VQYmFPw\n"
    "amd8GKMZQvP0ufGnUn7D7uartA==\n"
    "-----END CERTIFICATE-----\n";

static const char kCRLTestLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIDkDCCAnigAwIBAgICEAAwDQYJKoZIhvcNAQELBQAwTjELMAkGA1UEBhMCVVMx\n"
    "EzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxEjAQ\n"
    "BgNVBAoMCUJvcmluZ1NTTDAeFw0xNjA5MjYxNTA4MzFaFw0xNzA5MjYxNTA4MzFa\n"
    "MEsxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRIwEAYDVQQKDAlC\n"
    "b3JpbmdTU0wxEzARBgNVBAMMCmJvcmluZy5zc2wwggEiMA0GCSqGSIb3DQEBAQUA\n"
    "A4IBDwAwggEKAoIBAQDc5v1S1M0W+QWM+raWfO0LH8uvqEwuJQgODqMaGnSlWUx9\n"
    "8iQcnWfjyPja3lWg9K62hSOFDuSyEkysKHDxijz5R93CfLcfnVXjWQDJe7EJTTDP\n"
    "ozEvxN6RjAeYv7CF000euYr3QT5iyBjg76+bon1p0jHZBJeNPP1KqGYgyxp+hzpx\n"
    "e0gZmTlGAXd8JQK4v8kpdYwD6PPifFL/jpmQpqOtQmH/6zcLjY4ojmqpEdBqIKIX\n"
    "+saA29hMq0+NK3K+wgg31RU+cVWxu3tLOIiesETkeDgArjWRS1Vkzbi4v9SJxtNu\n"
    "OZuAxWiynRJw3JwH/OFHYZIvQqz68ZBoj96cepjPAgMBAAGjezB5MAkGA1UdEwQC\n"
    "MAAwLAYJYIZIAYb4QgENBB8WHU9wZW5TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRl\n"
    "MB0GA1UdDgQWBBTGn0OVVh/aoYt0bvEKG+PIERqnDzAfBgNVHSMEGDAWgBRY+3c3\n"
    "lxn8JG+5tuuSp8GcCqGr3jANBgkqhkiG9w0BAQsFAAOCAQEAd2nM8gCQN2Dc8QJw\n"
    "XSZXyuI3DBGGCHcay/3iXu0JvTC3EiQo8J6Djv7WLI0N5KH8mkm40u89fJAB2lLZ\n"
    "ShuHVtcC182bOKnePgwp9CNwQ21p0rDEu/P3X46ZvFgdxx82E9xLa0tBB8PiPDWh\n"
    "lV16jbaKTgX5AZqjnsyjR5o9/mbZVupZJXx5Syq+XA8qiJfstSYJs4KyKK9UOjql\n"
    "ICkJVKpi2ahDBqX4MOH4SLfzVk8pqSpviS6yaA1RXqjpkxiN45WWaXDldVHMSkhC\n"
    "5CNXsXi4b1nAntu89crwSLA3rEwzCWeYj+BX7e1T9rr3oJdwOU/2KQtW1js1yQUG\n"
    "tjJMFw==\n"
    "-----END CERTIFICATE-----\n";

static const char kBasicCRL[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBpzCBkAIBATANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJ\n"
    "Qm9yaW5nU1NMFw0xNjA5MjYxNTEwNTVaFw0xNjEwMjYxNTEwNTVaoA4wDDAKBgNV\n"
    "HRQEAwIBATANBgkqhkiG9w0BAQsFAAOCAQEAnrBKKgvd9x9zwK9rtUvVeFeJ7+LN\n"
    "ZEAc+a5oxpPNEsJx6hXoApYEbzXMxuWBQoCs5iEBycSGudct21L+MVf27M38KrWo\n"
    "eOkq0a2siqViQZO2Fb/SUFR0k9zb8xl86Zf65lgPplALun0bV/HT7MJcl04Tc4os\n"
    "dsAReBs5nqTGNEd5AlC1iKHvQZkM//MD51DspKnDpsDiUVi54h9C1SpfZmX8H2Vv\n"
    "diyu0fZ/bPAM3VAGawatf/SyWfBMyKpoPXEG39oAzmjjOj8en82psn7m474IGaho\n"
    "/vBbhl1ms5qQiLYPjm4YELtnXQoFyC72tBjbdFd/ZE9k4CNKDbxFUXFbkw==\n"
    "-----END X509 CRL-----\n";

static const char kRevokedCRL[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBvjCBpwIBATANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJ\n"
    "Qm9yaW5nU1NMFw0xNjA5MjYxNTEyNDRaFw0xNjEwMjYxNTEyNDRaMBUwEwICEAAX\n"
    "DTE2MDkyNjE1MTIyNlqgDjAMMAoGA1UdFAQDAgECMA0GCSqGSIb3DQEBCwUAA4IB\n"
    "AQCUGaM4DcWzlQKrcZvI8TMeR8BpsvQeo5BoI/XZu2a8h//PyRyMwYeaOM+3zl0d\n"
    "sjgCT8b3C1FPgT+P2Lkowv7rJ+FHJRNQkogr+RuqCSPTq65ha4WKlRGWkMFybzVH\n"
    "NloxC+aU3lgp/NlX9yUtfqYmJek1CDrOOGPrAEAwj1l/BUeYKNGqfBWYJQtPJu+5\n"
    "OaSvIYGpETCZJscUWODmLEb/O3DM438vLvxonwGqXqS0KX37+CHpUlyhnSovxXxp\n"
    "Pz4aF+L7OtczxL0GYtD2fR9B7TDMqsNmHXgQrixvvOY7MUdLGbd4RfJL3yA53hyO\n"
    "xzfKY2TzxLiOmctG0hXFkH5J\n"
    "-----END X509 CRL-----\n";

static const char kBadIssuerCRL[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBwjCBqwIBATANBgkqhkiG9w0BAQsFADBSMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEWMBQGA1UECgwN\n"
    "Tm90IEJvcmluZ1NTTBcNMTYwOTI2MTUxMjQ0WhcNMTYxMDI2MTUxMjQ0WjAVMBMC\n"
    "AhAAFw0xNjA5MjYxNTEyMjZaoA4wDDAKBgNVHRQEAwIBAjANBgkqhkiG9w0BAQsF\n"
    "AAOCAQEAlBmjOA3Fs5UCq3GbyPEzHkfAabL0HqOQaCP12btmvIf/z8kcjMGHmjjP\n"
    "t85dHbI4Ak/G9wtRT4E/j9i5KML+6yfhRyUTUJKIK/kbqgkj06uuYWuFipURlpDB\n"
    "cm81RzZaMQvmlN5YKfzZV/clLX6mJiXpNQg6zjhj6wBAMI9ZfwVHmCjRqnwVmCUL\n"
    "TybvuTmkryGBqREwmSbHFFjg5ixG/ztwzON/Ly78aJ8Bql6ktCl9+/gh6VJcoZ0q\n"
    "L8V8aT8+Ghfi+zrXM8S9BmLQ9n0fQe0wzKrDZh14EK4sb7zmOzFHSxm3eEXyS98g\n"
    "Od4cjsc3ymNk88S4jpnLRtIVxZB+SQ==\n"
    "-----END X509 CRL-----\n";

// kKnownCriticalCRL is kBasicCRL but with a critical issuing distribution point
// extension.
static const char kKnownCriticalCRL[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBujCBowIBATANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJ\n"
    "Qm9yaW5nU1NMFw0xNjA5MjYxNTEwNTVaFw0xNjEwMjYxNTEwNTVaoCEwHzAKBgNV\n"
    "HRQEAwIBATARBgNVHRwBAf8EBzAFoQMBAf8wDQYJKoZIhvcNAQELBQADggEBAA+3\n"
    "i+5e5Ub8sccfgOBs6WVJFI9c8gvJjrJ8/dYfFIAuCyeocs7DFXn1n13CRZ+URR/Q\n"
    "mVWgU28+xeusuSPYFpd9cyYTcVyNUGNTI3lwgcE/yVjPaOmzSZKdPakApRxtpKKQ\n"
    "NN/56aQz3bnT/ZSHQNciRB8U6jiD9V30t0w+FDTpGaG+7bzzUH3UVF9xf9Ctp60A\n"
    "3mfLe0scas7owSt4AEFuj2SPvcE7yvdOXbu+IEv21cEJUVExJAbhvIweHXh6yRW+\n"
    "7VVeiNzdIjkZjyTmAzoXGha4+wbxXyBRbfH+XWcO/H+8nwyG8Gktdu2QB9S9nnIp\n"
    "o/1TpfOMSGhMyMoyPrk=\n"
    "-----END X509 CRL-----\n";

// kUnknownCriticalCRL is kBasicCRL but with an unknown critical extension.
static const char kUnknownCriticalCRL[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBvDCBpQIBATANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJ\n"
    "Qm9yaW5nU1NMFw0xNjA5MjYxNTEwNTVaFw0xNjEwMjYxNTEwNTVaoCMwITAKBgNV\n"
    "HRQEAwIBATATBgwqhkiG9xIEAYS3CQABAf8EADANBgkqhkiG9w0BAQsFAAOCAQEA\n"
    "GvBP0xqL509InMj/3493YVRV+ldTpBv5uTD6jewzf5XdaxEQ/VjTNe5zKnxbpAib\n"
    "Kf7cwX0PMSkZjx7k7kKdDlEucwVvDoqC+O9aJcqVmM6GDyNb9xENxd0XCXja6MZC\n"
    "yVgP4AwLauB2vSiEprYJyI1APph3iAEeDm60lTXX/wBM/tupQDDujKh2GPyvBRfJ\n"
    "+wEDwGg3ICwvu4gO4zeC5qnFR+bpL9t5tOMAQnVZ0NWv+k7mkd2LbHdD44dxrfXC\n"
    "nhtfERx99SDmC/jtUAJrGhtCO8acr7exCeYcduN7KKCm91OeCJKK6OzWst0Og1DB\n"
    "kwzzU2rL3G65CrZ7H0SZsQ==\n"
    "-----END X509 CRL-----\n";

// kUnknownCriticalCRL2 is kBasicCRL but with a critical issuing distribution
// point extension followed by an unknown critical extension
static const char kUnknownCriticalCRL2[] =
    "-----BEGIN X509 CRL-----\n"
    "MIIBzzCBuAIBATANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJVUzETMBEGA1UE\n"
    "CAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzESMBAGA1UECgwJ\n"
    "Qm9yaW5nU1NMFw0xNjA5MjYxNTEwNTVaFw0xNjEwMjYxNTEwNTVaoDYwNDAKBgNV\n"
    "HRQEAwIBATARBgNVHRwBAf8EBzAFoQMBAf8wEwYMKoZIhvcSBAGEtwkAAQH/BAAw\n"
    "DQYJKoZIhvcNAQELBQADggEBACTcpQC8jXL12JN5YzOcQ64ubQIe0XxRAd30p7qB\n"
    "BTXGpgqBjrjxRfLms7EBYodEXB2oXMsDq3km0vT1MfYdsDD05S+SQ9CDsq/pUfaC\n"
    "E2WNI5p8WircRnroYvbN2vkjlRbMd1+yNITohXYXCJwjEOAWOx3XIM10bwPYBv4R\n"
    "rDobuLHoMgL3yHgMHmAkP7YpkBucNqeBV8cCdeAZLuhXFWi6yfr3r/X18yWbC/r2\n"
    "2xXdkrSqXLFo7ToyP8YKTgiXpya4x6m53biEYwa2ULlas0igL6DK7wjYZX95Uy7H\n"
    "GKljn9weIYiMPV/BzGymwfv2EW0preLwtyJNJPaxbdin6Jc=\n"
    "-----END X509 CRL-----\n";

// kEd25519Cert is a self-signed Ed25519 certificate.
static const char kEd25519Cert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBkTCCAUOgAwIBAgIJAJwooam0UCDmMAUGAytlcDBFMQswCQYDVQQGEwJBVTET\n"
    "MBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQ\n"
    "dHkgTHRkMB4XDTE0MDQyMzIzMjE1N1oXDTE0MDUyMzIzMjE1N1owRTELMAkGA1UE\n"
    "BhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGEludGVybmV0IFdp\n"
    "ZGdpdHMgUHR5IEx0ZDAqMAUGAytlcAMhANdamAGCsQq31Uv+08lkBzoO4XLz2qYj\n"
    "Ja8CGmj3B1Eao1AwTjAdBgNVHQ4EFgQUoux7eV+fJK2v3ah6QPU/lj1/+7UwHwYD\n"
    "VR0jBBgwFoAUoux7eV+fJK2v3ah6QPU/lj1/+7UwDAYDVR0TBAUwAwEB/zAFBgMr\n"
    "ZXADQQBuCzqji8VP9xU8mHEMjXGChX7YP5J664UyVKHKH9Z1u4wEbB8dJ3ScaWSL\n"
    "r+VHVKUhsrvcdCelnXRrrSD7xWAL\n"
    "-----END CERTIFICATE-----\n";

// kEd25519CertNull is an invalid self-signed Ed25519 with an explicit NULL in
// the signature algorithm.
static const char kEd25519CertNull[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBlTCCAUWgAwIBAgIJAJwooam0UCDmMAcGAytlcAUAMEUxCzAJBgNVBAYTAkFV\n"
    "MRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBXaWRnaXRz\n"
    "IFB0eSBMdGQwHhcNMTQwNDIzMjMyMTU3WhcNMTQwNTIzMjMyMTU3WjBFMQswCQYD\n"
    "VQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50ZXJuZXQg\n"
    "V2lkZ2l0cyBQdHkgTHRkMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPa\n"
    "piMlrwIaaPcHURqjUDBOMB0GA1UdDgQWBBSi7Ht5X58kra/dqHpA9T+WPX/7tTAf\n"
    "BgNVHSMEGDAWgBSi7Ht5X58kra/dqHpA9T+WPX/7tTAMBgNVHRMEBTADAQH/MAcG\n"
    "AytlcAUAA0EA70uefNocdJohkKPNROKVyBuBD3LXMyvmdTklsaxSRY3PcZdOohlr\n"
    "recgVPpVS7B+d9g4EwtZXIh4lodTBDHBBw==\n"
    "-----END CERTIFICATE-----\n";

// kSANTypesLeaf is a leaf certificate (signed by |kSANTypesRoot|) which
// contains SANS for example.com, test@example.com, 127.0.0.1, and
// https://example.com/. (The latter is useless for now since crypto/x509
// doesn't deal with URI SANs directly.)
static const char kSANTypesLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIClzCCAgCgAwIBAgIJAOjwnT/iW+qmMA0GCSqGSIb3DQEBCwUAMCsxFzAVBgNV\n"
    "BAoTDkJvcmluZ1NTTCBUZXN0MRAwDgYDVQQDEwdSb290IENBMB4XDTE1MDEwMTAw\n"
    "MDAwMFoXDTI1MDEwMTAwMDAwMFowLzEXMBUGA1UEChMOQm9yaW5nU1NMIFRlc3Qx\n"
    "FDASBgNVBAMTC2V4YW1wbGUuY29tMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKB\n"
    "gQDbRn2TLhInBki8Bighq37EtqJd/h5SRYh6NkelCA2SQlvCgcC+l3mYQPtPbRT9\n"
    "KxOLwqUuZ9jUCZ7WIji3Sgt0cyvCNPHRk+WW2XR781ifbGE8wLBB1NkrKyQjd1sc\n"
    "O711Xc4gVM+hY4cdHiTE8x0aUIuqthRD7ZendWL0FMhS1wIDAQABo4G+MIG7MA4G\n"
    "A1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYD\n"
    "VR0TAQH/BAIwADAZBgNVHQ4EEgQQn5EWH0NDPkmm3m22gNefYDAbBgNVHSMEFDAS\n"
    "gBBAN9cB+0AvuBx+VAQnjFkBMEQGA1UdEQQ9MDuCC2V4YW1wbGUuY29tgRB0ZXN0\n"
    "QGV4YW1wbGUuY29thwR/AAABhhRodHRwczovL2V4YW1wbGUuY29tLzANBgkqhkiG\n"
    "9w0BAQsFAAOBgQBtwJvY6+Tk6D6DOtDVaNoJ5y8E25CCuE/Ga4OuIcYJas+yLckf\n"
    "dZwUV3GUG2oBXl2MrpUFxXd4hKBO1CmlBY+hZEeIx0Yp6QWK9P/vnZeydOTP26mk\n"
    "jusJ2PqSmtKNU1Zcaba4d29oFejmOAfeguhR8AHpsc/zHEaS5Q9cJsuJcw==\n"
    "-----END CERTIFICATE-----\n";

// -----BEGIN RSA PRIVATE KEY-----
// MIICWwIBAAKBgQDbRn2TLhInBki8Bighq37EtqJd/h5SRYh6NkelCA2SQlvCgcC+
// l3mYQPtPbRT9KxOLwqUuZ9jUCZ7WIji3Sgt0cyvCNPHRk+WW2XR781ifbGE8wLBB
// 1NkrKyQjd1scO711Xc4gVM+hY4cdHiTE8x0aUIuqthRD7ZendWL0FMhS1wIDAQAB
// AoGACwf7z0i1DxOI2zSwFimLghfyCSp8mgT3fbZ3Wj0SebYu6ZUffjceneM/AVrq
// gGYHYLOVHcWJqfkl7X3hPo9SDhzLx0mM545/q21ZWCwjhswH7WiCEqV2/zeDO9WU
// NIO1VU0VoLm0AQ7ZvwnyB+fpgF9kkkDtbBJW7XWrfNVtlnECQQD97YENpEJ3X1kj
// 3rrkrHWDkKAyoWWY1i8Fm7LnganC9Bv6AVwgn5ZlE/479aWHF8vbOFEA3pFPiNZJ
// t9FTCfpJAkEA3RCXjGI0Y6GALFLwEs+nL/XZAfJaIpJEZVLCVosYQOSaMS4SchfC
// GGYVquT7ZgKk9uvz89Fg87OtBMWS9lrkHwJADGkGLKeBhBoJ3kHtem2fVK3F1pOi
// xoR5SdnhNYVVyaxqjZ5xZTrHe+stOrr3uxGDqhQniVZXXb6/Ul0Egv1y2QJAVg/h
// kAujba4wIhFf2VLyOZ+yjil1ocPj0LZ5Zgvcs1bMGJ1hHP3W2HzVrqRaowoggui1
// HpTC891dXGA2qKYV7QJAFDmT2A7OVvh3y4AEgzVwHrDmCMwMHKjCIntS7fjxrJnF
// YvJUG1zoHwUVrxxbR3DbpTODlktLcl/0b97D0IkH3w==
// -----END RSA PRIVATE KEY-----

static const char kSANTypesRoot[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICTTCCAbagAwIBAgIIAj5CwoHlWuYwDQYJKoZIhvcNAQELBQAwKzEXMBUGA1UE\n"
    "ChMOQm9yaW5nU1NMIFRlc3QxEDAOBgNVBAMTB1Jvb3QgQ0EwHhcNMTUwMTAxMDAw\n"
    "MDAwWhcNMjUwMTAxMDAwMDAwWjArMRcwFQYDVQQKEw5Cb3JpbmdTU0wgVGVzdDEQ\n"
    "MA4GA1UEAxMHUm9vdCBDQTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEA6Q5/\n"
    "EQzmWuaGg3D2UQcuAngR9bIkkjjuJmICx5TxPqF3asCP1SJotl3iTNrghRE1wpJy\n"
    "SY2BtIiXa7f8skRb2U0GcPkMxo/ps9+jaoRsQ1m+nbLQdpvD1/qZWcO45fNTA71J\n"
    "1rPMokP+rcILuQG4VimUAySnDSghKamulFtK+Z8CAwEAAaN6MHgwDgYDVR0PAQH/\n"
    "BAQDAgIEMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAPBgNVHRMBAf8E\n"
    "BTADAQH/MBkGA1UdDgQSBBBAN9cB+0AvuBx+VAQnjFkBMBsGA1UdIwQUMBKAEEA3\n"
    "1wH7QC+4HH5UBCeMWQEwDQYJKoZIhvcNAQELBQADgYEAc4N6hTE62/3gwg+kyc2f\n"
    "c/Jj1mHrOt+0NRaBnmvbmNpsEjHS96Ef4Wt/ZlPXPkkv1C1VosJnOIMF3Q522wRH\n"
    "bqaxARldS12VAa3gcWisDWD+SqSyDxjyojz0XDiJkTrFuCTCUiZO+1GLB7SO10Ms\n"
    "d5YVX0c90VMnUhF/dlrqS9U=\n"
    "-----END CERTIFICATE-----\n";

// -----BEGIN RSA PRIVATE KEY-----
// MIICXAIBAAKBgQDpDn8RDOZa5oaDcPZRBy4CeBH1siSSOO4mYgLHlPE+oXdqwI/V
// Imi2XeJM2uCFETXCknJJjYG0iJdrt/yyRFvZTQZw+QzGj+mz36NqhGxDWb6dstB2
// m8PX+plZw7jl81MDvUnWs8yiQ/6twgu5AbhWKZQDJKcNKCEpqa6UW0r5nwIDAQAB
// AoGALEF5daZqc+aEsp8X1yky3nsoheyPL0kqSBWii33IFemZgKcSaRnAoqjPWWLS
// 8dHj0I/4rej2MW8iuezVSpDak9tK5boHORC3w4p/wifkizQkLt1DANxTVbzcKvrt
// aZ7LjVaKkhjRJbLddniowFHkkWVbUccjvzcUd7Y2VuLbAhECQQDq4FE88aHio8zg
// bxSd0PwjEFwLYQTR19u812SoR8PmR6ofIL+pDwOV+fVs+OGcAAOgkhIukOrksQ4A
// 1cKtnyhXAkEA/gRI+u3tZ7UE1twIkBfZ6IvCdRodkPqHAYIxMRLzL+MhyZt4MEGc
// Ngb/F6U9/WOBFnoR/PI7IwE3ejutzKcL+QJBAKh+6eilk7QKPETZi1m3/dmNt+p1
// 3EZJ65pqjwxmB3Rg/vs7vCMk4TarTdSyKu+F1xRPFfoP/mK3Xctdjj6NyhsCQAYF
// 7/0TOzfkUPMPUJyqFB6xgbDpJ55ScnUUsznoqx+NkTWInDb4t02IqO/UmT2y6FKy
// Hk8TJ1fTJY+ebqaVp3ECQApx9gQ+n0zIhx97FMUuiRse73xkcW4+pZ8nF+8DmeQL
// /JKuuFGmzkG+rUbXFmo/Zg2ozVplw71NnQJ4znPsf7A=
// -----END RSA PRIVATE KEY-----

// The following four certificates were generated with this Go program, varying
// |includeNetscapeExtension| and defining rootKeyPEM and rootCertPEM to be
// strings containing the kSANTypesRoot, above.

// package main

// import (
//     "crypto/ecdsa"
//     "crypto/elliptic"
//     "crypto/rand"
//     "crypto/x509"
//     "crypto/x509/pkix"
//     "encoding/asn1"
//     "encoding/pem"
//     "math/big"
//     "os"
//     "time"
// )

// const includeNetscapeExtension = true

// func main() {
//     block, _ := pem.Decode([]byte(rootKeyPEM))
//     rootPriv, _ := x509.ParsePKCS1PrivateKey(block.Bytes)
//     block, _ = pem.Decode([]byte(rootCertPEM))
//     root, _ := x509.ParseCertificate(block.Bytes)

//     interTemplate := &x509.Certificate{
//         SerialNumber: big.NewInt(2),
//         Subject: pkix.Name{
//             CommonName: "No Basic Constraints (Netscape)",
//         },
//         NotBefore: time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC),
//         NotAfter:  time.Date(2099, time.January, 1, 0, 0, 0, 0, time.UTC),
//     }

//     if includeNetscapeExtension {
//         interTemplate.ExtraExtensions = []pkix.Extension{
//             pkix.Extension{
//                 Id:    asn1.ObjectIdentifier([]int{2, 16, 840, 1, 113730, 1, 1}),
//                 Value: []byte{0x03, 0x02, 2, 0x04},
//             },
//         }
//     } else {
//         interTemplate.KeyUsage = x509.KeyUsageCertSign
//     }

//     interKey, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)

//     interDER, err := x509.CreateCertificate(rand.Reader, interTemplate, root, &interKey.PublicKey, rootPriv)
//     if err != nil {
//         panic(err)
//     }

//     pem.Encode(os.Stdout, &pem.Block{Type: "CERTIFICATE", Bytes: interDER})

//     inter, _ := x509.ParseCertificate(interDER)

//     leafTemplate := &x509.Certificate{
//         SerialNumber: big.NewInt(3),
//         Subject: pkix.Name{
//             CommonName: "Leaf from CA with no Basic Constraints",
//         },
//         NotBefore:             time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC),
//         NotAfter:              time.Date(2099, time.January, 1, 0, 0, 0, 0, time.UTC),
//         BasicConstraintsValid: true,
//     }
//     leafKey, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)

//     leafDER, err := x509.CreateCertificate(rand.Reader, leafTemplate, inter, &leafKey.PublicKey, interKey)
//     if err != nil {
//         panic(err)
//     }

//     pem.Encode(os.Stdout, &pem.Block{Type: "CERTIFICATE", Bytes: leafDER})
// }

// kNoBasicConstraintsCertSignIntermediate doesn't have isCA set, but contains
// certSign in the keyUsage.
static const char kNoBasicConstraintsCertSignIntermediate[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBqjCCAROgAwIBAgIBAjANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowHzEdMBsGA1UEAxMUTm8gQmFzaWMgQ29uc3RyYWludHMw\n"
    "WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASEFMblfxIEDO8My7wHtHWTuDzNyID1\n"
    "OsPkMGkn32O/pSyXxXuAqDeFoMVffUMTyfm8JcYugSEbrv2qEXXM4bZRoy8wLTAO\n"
    "BgNVHQ8BAf8EBAMCAgQwGwYDVR0jBBQwEoAQQDfXAftAL7gcflQEJ4xZATANBgkq\n"
    "hkiG9w0BAQsFAAOBgQC1Lh6hIAm3K5kRh5iIydU0YAEm7eV6ZSskERDUq3DLJyl9\n"
    "ZUZCHUzvb464dkwZjeNzaUVS1pdElJslwX3DtGgeJLJGCnk8zUjBjaNrrDm0kzPW\n"
    "xKt/6oif1ci/KCKqKNXJAIFbc4e+IiBpenwpxHk3If4NM+Ek0nKoO8Uj0NkgTQ==\n"
    "-----END CERTIFICATE-----\n";

static const char kNoBasicConstraintsCertSignLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBUDCB96ADAgECAgEDMAoGCCqGSM49BAMCMB8xHTAbBgNVBAMTFE5vIEJhc2lj\n"
    "IENvbnN0cmFpbnRzMCAXDTAwMDEwMTAwMDAwMFoYDzIwOTkwMTAxMDAwMDAwWjAx\n"
    "MS8wLQYDVQQDEyZMZWFmIGZyb20gQ0Egd2l0aCBubyBCYXNpYyBDb25zdHJhaW50\n"
    "czBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEsYPMwzdJKjB+2gpC90ib2ilHoB\n"
    "w/arQ6ikUX0CNUDDaKaOu/jF39ogzVlg4lDFrjCKShSfCCcrwgONv70IZGijEDAO\n"
    "MAwGA1UdEwEB/wQCMAAwCgYIKoZIzj0EAwIDSAAwRQIgbV7R99yM+okXSIs6Fp3o\n"
    "eCOXiDL60IBxaTOcLS44ywcCIQDbn87Gj5cFgHBYAkzdHqDsyGXkxQTHDq9jmX24\n"
    "Djy3Zw==\n"
    "-----END CERTIFICATE-----\n";

// kNoBasicConstraintsNetscapeCAIntermediate doesn't have isCA set, but contains
// a Netscape certificate-type extension that asserts a type of "SSL CA".
static const char kNoBasicConstraintsNetscapeCAIntermediate[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBuDCCASGgAwIBAgIBAjANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowKjEoMCYGA1UEAxMfTm8gQmFzaWMgQ29uc3RyYWludHMg\n"
    "KE5ldHNjYXBlKTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABCeMbmCaOtMzXBqi\n"
    "PrCdNOH23CkaawUA+pAezitAN4RXS1O2CGK5sJjGPVVeogROU8G7/b+mU+ciZIzH\n"
    "1PP8FJKjMjAwMBsGA1UdIwQUMBKAEEA31wH7QC+4HH5UBCeMWQEwEQYJYIZIAYb4\n"
    "QgEBBAQDAgIEMA0GCSqGSIb3DQEBCwUAA4GBAAgNWjh7cfBTClTAk+Ml//5xb9Ju\n"
    "tkBhG6Rm+kkMD+qiSMO6t7xS7CsA0+jIBjkdEYaLZ3oxtQCBdZsVNxUvRxZ0AUfF\n"
    "G3DtRFTsrI1f7IQhpMuqEMF4shPW+5x54hrq0Fo6xMs6XoinJZcTUaaB8EeXRF6M\n"
    "P9p6HuyLrmn0c/F0\n"
    "-----END CERTIFICATE-----\n";

static const char kNoBasicConstraintsNetscapeCALeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBXDCCAQKgAwIBAgIBAzAKBggqhkjOPQQDAjAqMSgwJgYDVQQDEx9ObyBCYXNp\n"
    "YyBDb25zdHJhaW50cyAoTmV0c2NhcGUpMCAXDTAwMDEwMTAwMDAwMFoYDzIwOTkw\n"
    "MTAxMDAwMDAwWjAxMS8wLQYDVQQDEyZMZWFmIGZyb20gQ0Egd2l0aCBubyBCYXNp\n"
    "YyBDb25zdHJhaW50czBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABDlJKolDu3R2\n"
    "tPqSDycr0QJcWhxdBv76V0EEVflcHRxED6vAioTEcnQszt1OfKtBZvjlo0yp6i6Q\n"
    "DaYit0ZInmWjEDAOMAwGA1UdEwEB/wQCMAAwCgYIKoZIzj0EAwIDSAAwRQIhAJsh\n"
    "aZL6BHeEfoUBj1oZ2Ln91qzj3UCVMJ+vrmwAFdYyAiA3wp2JphgchvmoUFuzPXwj\n"
    "XyPwWPbymSTpzKhB4xB7qQ==\n"
    "-----END CERTIFICATE-----\n";

static const char kSelfSignedMismatchAlgorithms[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIFMjCCAxqgAwIBAgIJAL0mG5fOeJ7xMA0GCSqGSIb3DQEBDQUAMC0xCzAJBgNV\n"
    "BAYTAkdCMQ8wDQYDVQQHDAZMb25kb24xDTALBgNVBAoMBFRlc3QwIBcNMTgwOTE3\n"
    "MTIxNzU3WhgPMjExODA4MjQxMjE3NTdaMC0xCzAJBgNVBAYTAkdCMQ8wDQYDVQQH\n"
    "DAZMb25kb24xDTALBgNVBAoMBFRlc3QwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAw\n"
    "ggIKAoICAQDCMhBrRAGGw+n2GdctBr/cEK4FZA6ajiHjihgpCHoSBdyL4R2jGKLS\n"
    "g0WgaMXa1HpkKN7LcIySosEBPlmcRkr1RqbEvQStOSvoFCXYvtx3alM6HTbXMcDR\n"
    "mqoKoABP6LXsPSoMWIgqMtP2X9EOppzHVIK1yFYFfbIlvYUV2Ka+MuMe0Vh5wvD1\n"
    "4GanPb+cWSKgdRSVQovCCMY3yWtZKVEaxRpCsk/mYYIFWz0tcgMjIKwDx1XXgiAV\n"
    "nU6NK43xbaw3XhtnaD/pv9lhTTbNrlcln9LjTD097BaK4R+1AEPHnpfxA9Ui3upn\n"
    "kbsNUdGdOB0ksZi/vd7lh833YgquQUIAhYrbfvq/HFCpVV1gljzlS3sqULYpLE//\n"
    "i3OsuL2mE+CYIJGpIi2GeJJWXciNMTJDOqTn+fRDtVb4RPp4Y70DJirp7XzaBi3q\n"
    "H0edANCzPSRCDbZsOhzIXhXshldiXVRX666DDlbMQgLTEnNKrkwv6DmU8o15XQsb\n"
    "8k1Os2YwXmkEOxUQ7AJZXVTZSf6UK9Znmdq1ZrHjybMfRUkHVxJcnKvrxfryralv\n"
    "gzfvu+D6HuxrCo3Ojqa+nDgIbxKEBtdrcsMhq1jWPFhjwo1fSadAkKOfdCAuXJRD\n"
    "THg3b4Sf+W7Cpc570YHrIpBf7WFl2XsPcEM0mJZ5+yATASCubNozQwIDAQABo1Mw\n"
    "UTAdBgNVHQ4EFgQUES0hupZSqY21JOba10QyZuxm91EwHwYDVR0jBBgwFoAUES0h\n"
    "upZSqY21JOba10QyZuxm91EwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsF\n"
    "AAOCAgEABTN5S30ng/RMpBweDm2N561PdpaCdiRXtAFCRVWR2mkDYC/Xj9Vqe6be\n"
    "PyM7L/5OKYVjzF1yJu67z/dx+ja5o+41g17jdqla7hyPx+9B4uRyDh+1KJTa+duj\n"
    "mw/aA1LCr6O6W4WizDOsChJ6FaB2Y1+GlFnKWb5nUdhVJqXQE1WOX9dZnw8Y4Npd\n"
    "VmAsjWot0BZorJrt3fwfcv3QfA896twkbo7Llv/8qzg4sXZXZ4ZtgAOqnPngiSn+\n"
    "JT/vYCXZ406VvAFpFqMcVz2dO/VGuL8lGIMHRKNyafrsV81EzH1W/XmRWOgvgj6r\n"
    "yQI63ln/AMY72HQ97xLkE1xKunGz6bK5Ug5+O43Uftc4Mb6MUgzo+ZqEQ3Ob+cAV\n"
    "cvjmtwDaPO/O39O5Xq0tLTlkn2/cKf4OQ6S++GDxzyRVHh5JXgP4j9+jfZY57Woy\n"
    "R1bE7N50JjY4cDermBJKdlBIjL7UPhqmLyaG7V0hBitFlgGBUCcJtJOV0xYd5aF3\n"
    "pxNkvMXhBmh95fjxJ0cJjpO7tN1RAwtMMNgsl7OUbuVRQCHOPW5DgP5qY21jDeRn\n"
    "BY82382l+9QzykmJLI5MZnmj4BA9uIDCwMtoTTvP++SsvhUAbuvh7MOOUQL0EY4m\n"
    "KStYq7X9PKseN+PvmfeoffIKc5R/Ha39oi7cGMVHCr8aiEhsf94=\n"
    "-----END CERTIFICATE-----\n";

// kCommonNameWithSANs is a leaf certificate signed by kSANTypesRoot, with
// *.host1.test as the common name and a SAN list of *.host2.test and
// foo.host3.test.
static const char kCommonNameWithSANs[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIB2zCCAUSgAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowNzEeMBwGA1UEChMVQ29tbW9uIG5hbWUgd2l0aCBTQU5z\n"
    "MRUwEwYDVQQDDAwqLmhvc3QxLnRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNC\n"
    "AASgWzfnFnpQrokSLIC+LhCKJDUAY/2usfIDpOnafYoYCasbYetkmOslgyY4Nn07\n"
    "zjvjNROprA/0bdULXAkdL9bNo0gwRjAbBgNVHSMEFDASgBBAN9cB+0AvuBx+VAQn\n"
    "jFkBMCcGA1UdEQQgMB6CDCouaG9zdDIudGVzdIIOZm9vLmhvc3QzLnRlc3QwDQYJ\n"
    "KoZIhvcNAQELBQADgYEAtv2e3hBhsslXB1HTxgusjoschWOVtvGZUaYlhkKzKTCL\n"
    "4YpDn50BccnucBU/b9phYvaEZtyzOv4ZXhxTGyLnLrIVB9x5ikfCcfl+LNYNjDwM\n"
    "enm/h1zOfJ7wXLyscD4kU29Wc/zxBd70thIgLYn16CC1S9NtXKsXXDXv5VVH/bg=\n"
    "-----END CERTIFICATE-----\n";

// kCommonNameWithSANs is a leaf certificate signed by kSANTypesRoot, with
// *.host1.test as the common name and no SAN list.
static const char kCommonNameWithoutSANs[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBtTCCAR6gAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowOjEhMB8GA1UEChMYQ29tbW9uIG5hbWUgd2l0aG91dCBT\n"
    "QU5zMRUwEwYDVQQDDAwqLmhvc3QxLnRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMB\n"
    "BwNCAARt2vjlIrPE+kr11VS1rRP/AYQu4fvf1bNw/K9rwYlVBhmLMPYasEmpCtKE\n"
    "0bDIFydtDYC3wZDpSS+YiaG40sdAox8wHTAbBgNVHSMEFDASgBBAN9cB+0AvuBx+\n"
    "VAQnjFkBMA0GCSqGSIb3DQEBCwUAA4GBAHRbIeaCEytOpJpw9O2dlB656AHe1+t5\n"
    "4JiS5mvtzoVOLn7fFk5EFQtZS7sG1Uc2XjlSw+iyvFoTFEqfKyU/mIdc2vBuPwA2\n"
    "+YXT8aE4S+UZ9oz5j0gDpikGnkSCW0cyHD8L8fntNjaQRSaM482JpmtdmuxClmWO\n"
    "pFFXI2B5usgI\n"
    "-----END CERTIFICATE-----\n";

// kCommonNameWithEmailSAN is a leaf certificate signed by kSANTypesRoot, with
// *.host1.test as the common name and the email address test@host2.test in the
// SAN list.
static const char kCommonNameWithEmailSAN[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBvDCCASWgAwIBAgIBAjANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFzEVMBMGA1UEAwwMKi5ob3N0MS50ZXN0MFkwEwYHKoZI\n"
    "zj0CAQYIKoZIzj0DAQcDQgAEtevOxcTjpPzlNGoUMFfZyr1k03/Hiuh+EsnuScDs\n"
    "8XLKi6fDkvSaDClI99ycabQZRPIrvyT+dglDC6ugQd+CYqNJMEcwDAYDVR0TAQH/\n"
    "BAIwADAbBgNVHSMEFDASgBBAN9cB+0AvuBx+VAQnjFkBMBoGA1UdEQQTMBGBD3Rl\n"
    "c3RAaG9zdDIudGVzdDANBgkqhkiG9w0BAQsFAAOBgQCGbqb78OWJWl4zb+qw0Dz2\n"
    "HJgZZJt6/+nNG/XJKdaYeS4eofsbwsJI4fuuOF6ZvYCJxVNtGqdfZDgycvFA9hjv\n"
    "NGosBF1/spP17cmzTahLjxs71jDvHV/EQJbKGl/Zpta1Em1VrzSrwoOFabPXzZTJ\n"
    "aet/mER21Z/9ZsTUoJQPJw==\n"
    "-----END CERTIFICATE-----\n";

// kCommonNameWithIPSAN is a leaf certificate signed by kSANTypesRoot, with
// *.host1.test as the common name and the IP address 127.0.0.1 in the
// SAN list.
static const char kCommonNameWithIPSAN[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBsTCCARqgAwIBAgIBAjANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFzEVMBMGA1UEAwwMKi5ob3N0MS50ZXN0MFkwEwYHKoZI\n"
    "zj0CAQYIKoZIzj0DAQcDQgAEFKrgkxm8PysXbwnHQeTD3p8YY0+sY4ssnZgmj8wX\n"
    "KTyn893fdBHWlz71GO6t82wMTF5d+ZYwI2XU52pfl4SB2aM+MDwwDAYDVR0TAQH/\n"
    "BAIwADAbBgNVHSMEFDASgBBAN9cB+0AvuBx+VAQnjFkBMA8GA1UdEQQIMAaHBH8A\n"
    "AAEwDQYJKoZIhvcNAQELBQADgYEAQWZ8Oj059ZjS109V/ijMYT28xuAN5n6HHxCO\n"
    "DopTP56Zu9+gme5wTETWEfocspZvgecoUOcedTFoKSQ7JafO09NcVLA+D6ddYpju\n"
    "mgfuiLy9dDhqvX/NHaLBMxOBWWbOLwWE+ibyX+pOzjWRCw1L7eUXOr6PhZAOQsmU\n"
    "D0+O6KI=\n"
    "-----END CERTIFICATE-----\n";

// kConstrainedIntermediate is an intermediate signed by kSANTypesRoot, with
// permitted DNS names of permitted1.test and foo.permitted2.test and an
// excluded DNS name of excluded.permitted1.test. Its private key is:
//
// -----BEGIN PRIVATE KEY-----
// MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgTXUM4tJWM7OzATty
// JhNOfIv/d8heWFBeKOfMR+RfaROhRANCAASbbbWYiN6mn+BCpg4XNpibOH0D/DN4
// kZ5C/Ml2YVomC9T83OKk2CzB8fPAabPb4P4Vv+fIabpEfjWS5nzKLY1y
// -----END PRIVATE KEY-----
static const char kConstrainedIntermediate[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIICDjCCAXegAwIBAgIBAjANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowKDEmMCQGA1UEAxMdTmFtZSBDb25zdHJhaW50cyBJbnRl\n"
    "cm1lZGlhdGUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASbbbWYiN6mn+BCpg4X\n"
    "NpibOH0D/DN4kZ5C/Ml2YVomC9T83OKk2CzB8fPAabPb4P4Vv+fIabpEfjWS5nzK\n"
    "LY1yo4GJMIGGMA8GA1UdEwEB/wQFMAMBAf8wGwYDVR0jBBQwEoAQQDfXAftAL7gc\n"
    "flQEJ4xZATBWBgNVHR4BAf8ETDBKoCowEYIPcGVybWl0dGVkMS50ZXN0MBWCE2Zv\n"
    "by5wZXJtaXR0ZWQyLnRlc3ShHDAaghhleGNsdWRlZC5wZXJtaXR0ZWQxLnRlc3Qw\n"
    "DQYJKoZIhvcNAQELBQADgYEAFq1Ka05hiKREwRpSceQPzIIH4B5a5IVBg5/EvmQI\n"
    "9V0fXyAE1GmahPt70sIBxIgzNTEaY8P/IoOuCdlZWe0msmyEO3S6YSAzOWR5Van6\n"
    "cXmFM1uMd95TlkxUMRdV+jKJTvG6R/BM2zltaV7Xt662k5HtzT5Svw0rZlFaggZz\n"
    "UyM=\n"
    "-----END CERTIFICATE-----\n";

// kCommonNamePermittedLeaf is a leaf certificate signed by
// kConstrainedIntermediate. Its common name is permitted by the name
// constraints.
static const char kCommonNamePermittedLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBaDCCAQ2gAwIBAgIBAzAKBggqhkjOPQQDAjAoMSYwJAYDVQQDEx1OYW1lIENv\n"
    "bnN0cmFpbnRzIEludGVybWVkaWF0ZTAgFw0wMDAxMDEwMDAwMDBaGA8yMDk5MDEw\n"
    "MTAwMDAwMFowPjEeMBwGA1UEChMVQ29tbW9uIG5hbWUgcGVybWl0dGVkMRwwGgYD\n"
    "VQQDExNmb28ucGVybWl0dGVkMS50ZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD\n"
    "QgAENX5Ycs8q8MRzPYUz6DqLHhJR3wcmniFRgkiEa7MxE/mRe00y0VGwH7xi7Aoc\n"
    "emXPrtD4JwN5bssbcxWGAKYYzaMQMA4wDAYDVR0TAQH/BAIwADAKBggqhkjOPQQD\n"
    "AgNJADBGAiEAtsnWuRQXtw2xbieC78Y8SVEtTjcZUx8uZyQe1GPLfGICIQDR4fNY\n"
    "yg3PC94ydPNQZVsFxAne32CbonWWsokalTFpUQ==\n"
    "-----END CERTIFICATE-----\n";
static const char kCommonNamePermitted[] = "foo.permitted1.test";

// kCommonNameNotPermittedLeaf is a leaf certificate signed by
// kConstrainedIntermediate. Its common name is not permitted by the name
// constraints.
static const char kCommonNameNotPermittedLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBazCCARCgAwIBAgIBBDAKBggqhkjOPQQDAjAoMSYwJAYDVQQDEx1OYW1lIENv\n"
    "bnN0cmFpbnRzIEludGVybWVkaWF0ZTAgFw0wMDAxMDEwMDAwMDBaGA8yMDk5MDEw\n"
    "MTAwMDAwMFowQTEiMCAGA1UEChMZQ29tbW9uIG5hbWUgbm90IHBlcm1pdHRlZDEb\n"
    "MBkGA1UEAxMSbm90LXBlcm1pdHRlZC50ZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n"
    "AQcDQgAEzfghKuWf0JoXb0Drp09C3yXMSQQ1byt+AUaymvsHOWsxQ9v1Q+vkF/IM\n"
    "HRqGTk2TyxrB2iClVEn/Uu+YtYox1KMQMA4wDAYDVR0TAQH/BAIwADAKBggqhkjO\n"
    "PQQDAgNJADBGAiEAxaUslxmoWL1tIvnDz7gDkto/HcmdU0jHVuUQLXcCG8wCIQCN\n"
    "5xZjitlCQU8UB5qSu9wH4B+0JcVO3Ss4Az76HEJWMw==\n"
    "-----END CERTIFICATE-----\n";
static const char kCommonNameNotPermitted[] = "not-permitted.test";

// kCommonNameNotPermittedWithSANsLeaf is a leaf certificate signed by
// kConstrainedIntermediate. Its common name is not permitted by the name
// constraints but it has a SAN list.
static const char kCommonNameNotPermittedWithSANsLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBqTCCAU+gAwIBAgIBBjAKBggqhkjOPQQDAjAoMSYwJAYDVQQDEx1OYW1lIENv\n"
    "bnN0cmFpbnRzIEludGVybWVkaWF0ZTAgFw0wMDAxMDEwMDAwMDBaGA8yMDk5MDEw\n"
    "MTAwMDAwMFowSzEsMCoGA1UEChMjQ29tbW9uIG5hbWUgbm90IHBlcm1pdHRlZCB3\n"
    "aXRoIFNBTlMxGzAZBgNVBAMTEm5vdC1wZXJtaXR0ZWQudGVzdDBZMBMGByqGSM49\n"
    "AgEGCCqGSM49AwEHA0IABKsn9wOApXFHrqhLdQgbFSeaSoAIbxgO0zVSRZUb5naR\n"
    "93zoL3MFOvZEF8xiEqh7le+l3XuUig0fwqpcsZzRNJajRTBDMAwGA1UdEwEB/wQC\n"
    "MAAwMwYDVR0RBCwwKoITZm9vLnBlcm1pdHRlZDEudGVzdIITZm9vLnBlcm1pdHRl\n"
    "ZDIudGVzdDAKBggqhkjOPQQDAgNIADBFAiACk+1f184KkKAXuntmrz+Ygcq8MiZl\n"
    "4delx44FtcNaegIhAIA5nYfzxNcTXxDo3U+x1vSLH6Y7faLvHiFySp7O//q+\n"
    "-----END CERTIFICATE-----\n";
static const char kCommonNameNotPermittedWithSANs[] = "not-permitted.test";

// kCommonNameNotDNSLeaf is a leaf certificate signed by
// kConstrainedIntermediate. Its common name is not a DNS name.
static const char kCommonNameNotDNSLeaf[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBYTCCAQagAwIBAgIBCDAKBggqhkjOPQQDAjAoMSYwJAYDVQQDEx1OYW1lIENv\n"
    "bnN0cmFpbnRzIEludGVybWVkaWF0ZTAgFw0wMDAxMDEwMDAwMDBaGA8yMDk5MDEw\n"
    "MTAwMDAwMFowNzEcMBoGA1UEChMTQ29tbW9uIG5hbWUgbm90IEROUzEXMBUGA1UE\n"
    "AxMOTm90IGEgRE5TIG5hbWUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASnueyc\n"
    "Zxtnw5ke2J2T0/LwAK37auQP/RSFd9mem+BJVbgviawtAlignJmafp7Zw4/GdYEJ\n"
    "Vm8qlriOJtluvXGcoxAwDjAMBgNVHRMBAf8EAjAAMAoGCCqGSM49BAMCA0kAMEYC\n"
    "IQChUAmVNI39VHe0zemRE09VDcSEgOxr1nTvjLcg/Q8pVQIhAJYZnJI0YZAi05QH\n"
    "RHNlAkTK2TnUaVn3fGSylaLiFS1r\n"
    "-----END CERTIFICATE-----\n";
static const char kCommonNameNotDNS[] = "Not a DNS name";

// The following six certificates are issued by |kSANTypesRoot| and have
// different extended key usage values. They were created with the following
// Go program:
//
// func main() {
//     block, _ := pem.Decode([]byte(rootKeyPEM))
//     rootPriv, _ := x509.ParsePKCS1PrivateKey(block.Bytes)
//     block, _ = pem.Decode([]byte(rootCertPEM))
//     root, _ := x509.ParseCertificate(block.Bytes)
//
//     leafTemplate := &x509.Certificate{
//         SerialNumber: big.NewInt(3),
//         Subject: pkix.Name{
//             CommonName: "EKU msSGC",
//         },
//         NotBefore:             time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC),
//         NotAfter:              time.Date(2099, time.January, 1, 0, 0, 0, 0, time.UTC),
//         BasicConstraintsValid: true,
//         ExtKeyUsage:           []x509.ExtKeyUsage{FILL IN HERE},
//     }
//     leafKey, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
//     leafDER, err := x509.CreateCertificate(rand.Reader, leafTemplate, root, &leafKey.PublicKey, rootPriv)
//     if err != nil {
//         panic(err)
//     }
//     pem.Encode(os.Stdout, &pem.Block{Type: "CERTIFICATE", Bytes: leafDER})
// }

static const char kMicrosoftSGCCert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBtDCCAR2gAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEEn61v3Vs+q6bTyyRnrJvuKBE8PTNVLbXGB52jig4Qse2\n"
    "mGygNEysS0uzZ0luz+rn2hDRUFL6sHLUs1d8UMbI/6NEMEIwFQYDVR0lBA4wDAYK\n"
    "KwYBBAGCNwoDAzAMBgNVHRMBAf8EAjAAMBsGA1UdIwQUMBKAEEA31wH7QC+4HH5U\n"
    "BCeMWQEwDQYJKoZIhvcNAQELBQADgYEAgDQI9RSo3E3ZVnU71TV/LjG9xwHtfk6I\n"
    "rlNnlJJ0lsTHAuMc1mwCbzhtsmasetwYlIa9G8GFWB9Gh/QqHA7G649iGGmXShqe\n"
    "aVDuWgeSEJxBPE2jILoMm4pEYF7jfonTn7XXX6O78yuSlP+NPIU0gUKHkWZ1sWk0\n"
    "cC4l0r/6jik=\n"
    "-----END CERTIFICATE-----\n";

static const char kNetscapeSGCCert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBszCCARygAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAE3NbT+TnBfq1DWJCezjaUL52YhDU7cOkI2S2PoWgJ1v7x\n"
    "kKLwBonUFZjppZs69SyBHeJdti+KoJ3qTW+hCG08EaNDMEEwFAYDVR0lBA0wCwYJ\n"
    "YIZIAYb4QgQBMAwGA1UdEwEB/wQCMAAwGwYDVR0jBBQwEoAQQDfXAftAL7gcflQE\n"
    "J4xZATANBgkqhkiG9w0BAQsFAAOBgQBuiyVcfazekHkCWksxdFmjPmMtWCxFjkzc\n"
    "8VBxFE0CfSHQAfZ8J7tXd1FbAq/eXdZvvo8v0JB4sOM4Ex1ob1fuvDFHdSAHAD7W\n"
    "dhKIjJyzVojoxjCjyue0XMeEPl7RiqbdxoS/R5HFAqAF0T2OeQAqP9gTpOXoau1M\n"
    "RQHX6HQJJg==\n"
    "-----END CERTIFICATE-----\n";

static const char kServerEKUCert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBsjCCARugAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEDd35i+VWPwIOKLrLWTuP5cqD+yJDB5nujEzPgkXP5LKJ\n"
    "SZRbHTqTdpYZB2jy6y90RY2Bsjx7FfZ7nN5G2g1GOKNCMEAwEwYDVR0lBAwwCgYI\n"
    "KwYBBQUHAwEwDAYDVR0TAQH/BAIwADAbBgNVHSMEFDASgBBAN9cB+0AvuBx+VAQn\n"
    "jFkBMA0GCSqGSIb3DQEBCwUAA4GBAIKmbMBjuivL/rxDu7u7Vr3o3cdmEggBJxwL\n"
    "iatNW3x1wg0645aNYOktW/iQ7mAAiziTY73GFyfiJDWqnY+CwA94ZWyQidjHdN/I\n"
    "6BR52sN/dkYEoInYEbmDNMc/if+T0yqeBQLP4BeKLiT8p0qqaimae6LgibS19hDP\n"
    "2hoEMdz2\n"
    "-----END CERTIFICATE-----\n";

static const char kServerEKUPlusMicrosoftSGCCert[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBvjCCASegAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEDO1MYPxq+U4oXMIK8UnsS4C696wpcu4UOmcMJJ5CUd5Z\n"
    "ZpJShN6kYKnrb3GK/6xEgbUGntmrzSRG5FYqk6QgD6NOMEwwHwYDVR0lBBgwFgYI\n"
    "KwYBBQUHAwEGCisGAQQBgjcKAwMwDAYDVR0TAQH/BAIwADAbBgNVHSMEFDASgBBA\n"
    "N9cB+0AvuBx+VAQnjFkBMA0GCSqGSIb3DQEBCwUAA4GBAHOu2IBa4lHzVGS36HxS\n"
    "SejUE87Ji1ysM6BgkYbfxfS9MuV+J3UnqH57JjbH/3CFl4ZDWceF6SGBSCn8LqKa\n"
    "KHpwoNFU3zA99iQzVJgbUyN0PbKwHEanLyKDJZyFk71R39ToxhSNQgaQYjZYCy1H\n"
    "5V9oXd1bodEqVsOZ/mur24Ku\n"
    "-----END CERTIFICATE-----\n";

static const char kAnyEKU[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBrjCCARegAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAE9nsLABDporlTvx1OBUc4Hd5vxfX+8nS/OhbHmKtFLYNu\n"
    "1CLLrImbwMQYD2G+PgLO6sQHmASq2jmJKp6ZWsRkTqM+MDwwDwYDVR0lBAgwBgYE\n"
    "VR0lADAMBgNVHRMBAf8EAjAAMBsGA1UdIwQUMBKAEEA31wH7QC+4HH5UBCeMWQEw\n"
    "DQYJKoZIhvcNAQELBQADgYEAxgjgn1SAzQ+2GeCicZ5ndvVhKIeFelGCQ989XTVq\n"
    "uUbAYBW6v8GXNuVzoXYxDgNSanF6U+w+INrJ6daKVrIxAxdk9QFgBXqJoupuRAA3\n"
    "/OqnmYux0EqOTLbTK1P8DhaiaD0KV6dWGUwzqsgBmPkZ0lgNaPjvb1mKV3jhBkjz\n"
    "L6A=\n"
    "-----END CERTIFICATE-----\n";

static const char kNoEKU[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBnTCCAQagAwIBAgIBAzANBgkqhkiG9w0BAQsFADArMRcwFQYDVQQKEw5Cb3Jp\n"
    "bmdTU0wgVGVzdDEQMA4GA1UEAxMHUm9vdCBDQTAgFw0wMDAxMDEwMDAwMDBaGA8y\n"
    "MDk5MDEwMTAwMDAwMFowFDESMBAGA1UEAxMJRUtVIG1zU0dDMFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEpSFSqbYY86ZcMamE606dqdyjWlwhSHKOLUFsUUIzkMPz\n"
    "KHRu/x3Yzi8+Hm8eFK/TnCbkpYsYw4hIw00176dYzaMtMCswDAYDVR0TAQH/BAIw\n"
    "ADAbBgNVHSMEFDASgBBAN9cB+0AvuBx+VAQnjFkBMA0GCSqGSIb3DQEBCwUAA4GB\n"
    "AHvYzynIkjLThExHRS+385hfv4vgrQSMmCM1SAnEIjSBGsU7RPgiGAstN06XivuF\n"
    "T1fNugRmTu4OtOIbfdYkcjavJufw9hR9zWTt77CNMTy9XmOZLgdS5boFTtLCztr3\n"
    "TXHOSQQD8Dl4BK0wOet+TP6LBEjHlRFjAqK4bu9xpxV2\n"
    "-----END CERTIFICATE-----\n";

// CertFromPEM parses the given, NUL-terminated pem block and returns an
// |X509*|.
static bssl::UniquePtr<X509> CertFromPEM(const char *pem) {
  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(pem, strlen(pem)));
  return bssl::UniquePtr<X509>(
      PEM_read_bio_X509(bio.get(), nullptr, nullptr, nullptr));
}

// CRLFromPEM parses the given, NUL-terminated pem block and returns an
// |X509_CRL*|.
static bssl::UniquePtr<X509_CRL> CRLFromPEM(const char *pem) {
  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(pem, strlen(pem)));
  return bssl::UniquePtr<X509_CRL>(
      PEM_read_bio_X509_CRL(bio.get(), nullptr, nullptr, nullptr));
}

// PrivateKeyFromPEM parses the given, NUL-terminated pem block and returns an
// |EVP_PKEY*|.
static bssl::UniquePtr<EVP_PKEY> PrivateKeyFromPEM(const char *pem) {
  bssl::UniquePtr<BIO> bio(
      BIO_new_mem_buf(const_cast<char *>(pem), strlen(pem)));
  return bssl::UniquePtr<EVP_PKEY>(
      PEM_read_bio_PrivateKey(bio.get(), nullptr, nullptr, nullptr));
}

// CertsToStack converts a vector of |X509*| to an OpenSSL STACK_OF(X509),
// bumping the reference counts for each certificate in question.
static bssl::UniquePtr<STACK_OF(X509)> CertsToStack(
    const std::vector<X509 *> &certs) {
  bssl::UniquePtr<STACK_OF(X509)> stack(sk_X509_new_null());
  if (!stack) {
    return nullptr;
  }
  for (auto cert : certs) {
    if (!bssl::PushToStack(stack.get(), bssl::UpRef(cert))) {
      return nullptr;
    }
  }

  return stack;
}

// CRLsToStack converts a vector of |X509_CRL*| to an OpenSSL
// STACK_OF(X509_CRL), bumping the reference counts for each CRL in question.
static bssl::UniquePtr<STACK_OF(X509_CRL)> CRLsToStack(
    const std::vector<X509_CRL *> &crls) {
  bssl::UniquePtr<STACK_OF(X509_CRL)> stack(sk_X509_CRL_new_null());
  if (!stack) {
    return nullptr;
  }
  for (auto crl : crls) {
    if (!bssl::PushToStack(stack.get(), bssl::UpRef(crl))) {
      return nullptr;
    }
  }

  return stack;
}

static int Verify(X509 *leaf, const std::vector<X509 *> &roots,
                  const std::vector<X509 *> &intermediates,
                  const std::vector<X509_CRL *> &crls, unsigned long flags,
                  bool use_additional_untrusted,
                  std::function<void(X509_VERIFY_PARAM *)> configure_callback,
                  int (*verify_callback)(int, X509_STORE_CTX *) = nullptr) {
  bssl::UniquePtr<STACK_OF(X509)> roots_stack(CertsToStack(roots));
  bssl::UniquePtr<STACK_OF(X509)> intermediates_stack(
      CertsToStack(intermediates));
  bssl::UniquePtr<STACK_OF(X509_CRL)> crls_stack(CRLsToStack(crls));

  if (!roots_stack ||
      !intermediates_stack ||
      !crls_stack) {
    return X509_V_ERR_UNSPECIFIED;
  }

  bssl::UniquePtr<X509_STORE_CTX> ctx(X509_STORE_CTX_new());
  bssl::UniquePtr<X509_STORE> store(X509_STORE_new());
  if (!ctx ||
      !store) {
    return X509_V_ERR_UNSPECIFIED;
  }

  if (use_additional_untrusted) {
    X509_STORE_set0_additional_untrusted(store.get(),
                                         intermediates_stack.get());
  }

  if (!X509_STORE_CTX_init(
          ctx.get(), store.get(), leaf,
          use_additional_untrusted ? nullptr : intermediates_stack.get())) {
    return X509_V_ERR_UNSPECIFIED;
  }

  X509_STORE_CTX_trusted_stack(ctx.get(), roots_stack.get());
  X509_STORE_CTX_set0_crls(ctx.get(), crls_stack.get());

  X509_VERIFY_PARAM *param = X509_VERIFY_PARAM_new();
  if (param == nullptr) {
    return X509_V_ERR_UNSPECIFIED;
  }
  X509_VERIFY_PARAM_set_time(param, 1474934400 /* Sep 27th, 2016 */);
  X509_VERIFY_PARAM_set_depth(param, 16);
  if (configure_callback) {
    configure_callback(param);
  }
  if (flags) {
    X509_VERIFY_PARAM_set_flags(param, flags);
  }
  X509_STORE_CTX_set0_param(ctx.get(), param);

  ERR_clear_error();
  if (X509_verify_cert(ctx.get()) != 1) {
    return X509_STORE_CTX_get_error(ctx.get());
  }

  return X509_V_OK;
}

static int Verify(X509 *leaf, const std::vector<X509 *> &roots,
                   const std::vector<X509 *> &intermediates,
                   const std::vector<X509_CRL *> &crls,
                   unsigned long flags = 0) {
  const int r1 =
      Verify(leaf, roots, intermediates, crls, flags, false, nullptr);
  const int r2 =
      Verify(leaf, roots, intermediates, crls, flags, true, nullptr);

  if (r1 != r2) {
    fprintf(stderr,
            "Verify with, and without, use_additional_untrusted gave different "
            "results: %d vs %d.\n",
            r1, r2);
    return false;
  }

  return r1;
}

TEST(X509Test, TestVerify) {
  bssl::UniquePtr<X509> cross_signing_root(CertFromPEM(kCrossSigningRootPEM));
  bssl::UniquePtr<X509> root(CertFromPEM(kRootCAPEM));
  bssl::UniquePtr<X509> root_cross_signed(CertFromPEM(kRootCrossSignedPEM));
  bssl::UniquePtr<X509> intermediate(CertFromPEM(kIntermediatePEM));
  bssl::UniquePtr<X509> intermediate_self_signed(
      CertFromPEM(kIntermediateSelfSignedPEM));
  bssl::UniquePtr<X509> leaf(CertFromPEM(kLeafPEM));
  bssl::UniquePtr<X509> leaf_no_key_usage(CertFromPEM(kLeafNoKeyUsagePEM));
  bssl::UniquePtr<X509> forgery(CertFromPEM(kForgeryPEM));

  ASSERT_TRUE(cross_signing_root);
  ASSERT_TRUE(root);
  ASSERT_TRUE(root_cross_signed);
  ASSERT_TRUE(intermediate);
  ASSERT_TRUE(intermediate_self_signed);
  ASSERT_TRUE(leaf);
  ASSERT_TRUE(forgery);
  ASSERT_TRUE(leaf_no_key_usage);

  std::vector<X509*> empty;
  std::vector<X509_CRL*> empty_crls;
  ASSERT_EQ(X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY,
            Verify(leaf.get(), empty, empty, empty_crls));
  ASSERT_EQ(X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY,
            Verify(leaf.get(), empty, {intermediate.get()}, empty_crls));

  ASSERT_EQ(X509_V_OK,
            Verify(leaf.get(), {root.get()}, {intermediate.get()}, empty_crls));
  ASSERT_EQ(X509_V_OK,
            Verify(leaf.get(), {cross_signing_root.get()},
                   {intermediate.get(), root_cross_signed.get()}, empty_crls));
  ASSERT_EQ(X509_V_OK,
            Verify(leaf.get(), {cross_signing_root.get(), root.get()},
                   {intermediate.get(), root_cross_signed.get()}, empty_crls));

  /* This is the “altchains” test – we remove the cross-signing CA but include
   * the cross-sign in the intermediates. */
  ASSERT_EQ(X509_V_OK,
            Verify(leaf.get(), {root.get()},
                   {intermediate.get(), root_cross_signed.get()}, empty_crls));
  ASSERT_EQ(X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY,
            Verify(leaf.get(), {root.get()},
                   {intermediate.get(), root_cross_signed.get()}, empty_crls,
                   X509_V_FLAG_NO_ALT_CHAINS));
  ASSERT_EQ(X509_V_ERR_INVALID_CA,
            Verify(forgery.get(), {intermediate_self_signed.get()},
                   {leaf_no_key_usage.get()}, empty_crls));

  /* Test that one cannot skip Basic Constraints checking with a contorted set
   * of roots and intermediates. This is a regression test for CVE-2015-1793. */
  ASSERT_EQ(X509_V_ERR_INVALID_CA,
            Verify(forgery.get(),
                   {intermediate_self_signed.get(), root_cross_signed.get()},
                   {leaf_no_key_usage.get(), intermediate.get()}, empty_crls));
}

static const char kHostname[] = "example.com";
static const char kWrongHostname[] = "example2.com";
static const char kEmail[] = "test@example.com";
static const char kWrongEmail[] = "test2@example.com";
static const uint8_t kIP[4] = {127, 0, 0, 1};
static const uint8_t kWrongIP[4] = {127, 0, 0, 2};
static const char kIPString[] = "127.0.0.1";
static const char kWrongIPString[] = "127.0.0.2";

TEST(X509Test, ZeroLengthsWithX509PARAM) {
  bssl::UniquePtr<X509> leaf(CertFromPEM(kSANTypesLeaf));
  bssl::UniquePtr<X509> root(CertFromPEM(kSANTypesRoot));
  ASSERT_TRUE(leaf);
  ASSERT_TRUE(root);

  std::vector<X509_CRL *> empty_crls;

  struct X509Test {
    const char *correct_value;
    size_t correct_value_len;
    const char *incorrect_value;
    size_t incorrect_value_len;
    int (*func)(X509_VERIFY_PARAM *, const char *, size_t);
    int mismatch_error;
  };
  const std::vector<X509Test> kTests = {
      {kHostname, strlen(kHostname), kWrongHostname, strlen(kWrongHostname),
       X509_VERIFY_PARAM_set1_host, X509_V_ERR_HOSTNAME_MISMATCH},
      {kEmail, strlen(kEmail), kWrongEmail, strlen(kWrongEmail),
       X509_VERIFY_PARAM_set1_email, X509_V_ERR_EMAIL_MISMATCH},
  };

  for (size_t i = 0; i < kTests.size(); i++) {
    SCOPED_TRACE(i);
    const X509Test &test = kTests[i];

    // The correct value should work.
    ASSERT_EQ(X509_V_OK,
              Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                     [&test](X509_VERIFY_PARAM *param) {
                       ASSERT_TRUE(test.func(param, test.correct_value,
                                             test.correct_value_len));
                     }));

    // The wrong value should trigger a verification error.
    ASSERT_EQ(test.mismatch_error,
              Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                     [&test](X509_VERIFY_PARAM *param) {
                       ASSERT_TRUE(test.func(param, test.incorrect_value,
                                             test.incorrect_value_len));
                     }));

    // Passing zero as the length, unlike OpenSSL, should trigger an error and
    // should cause verification to fail.
    ASSERT_EQ(X509_V_ERR_INVALID_CALL,
              Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                     [&test](X509_VERIFY_PARAM *param) {
                       ASSERT_FALSE(test.func(param, test.correct_value, 0));
                     }));

    // Passing an empty value should be an error when setting and should cause
    // verification to fail.
    ASSERT_EQ(X509_V_ERR_INVALID_CALL,
              Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                     [&test](X509_VERIFY_PARAM *param) {
                       ASSERT_FALSE(test.func(param, nullptr, 0));
                     }));

    // Passing a value with embedded NULs should also be an error and should
    // also cause verification to fail.
    ASSERT_EQ(X509_V_ERR_INVALID_CALL,
              Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                     [&test](X509_VERIFY_PARAM *param) {
                       ASSERT_FALSE(test.func(param, "a", 2));
                     }));
  }

  // IP addresses work slightly differently:

  // The correct value should still work.
  ASSERT_EQ(X509_V_OK, Verify(leaf.get(), {root.get()}, {}, empty_crls, 0,
                              false, [](X509_VERIFY_PARAM *param) {
                                ASSERT_TRUE(X509_VERIFY_PARAM_set1_ip(
                                    param, kIP, sizeof(kIP)));
                              }));

  // Incorrect values should still fail.
  ASSERT_EQ(X509_V_ERR_IP_ADDRESS_MISMATCH,
            Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                   [](X509_VERIFY_PARAM *param) {
                     ASSERT_TRUE(X509_VERIFY_PARAM_set1_ip(param, kWrongIP,
                                                           sizeof(kWrongIP)));
                   }));

  // Zero length values should trigger an error when setting and cause
  // verification to always fail.
  ASSERT_EQ(X509_V_ERR_INVALID_CALL,
            Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                   [](X509_VERIFY_PARAM *param) {
                     ASSERT_FALSE(X509_VERIFY_PARAM_set1_ip(param, kIP, 0));
                   }));

  // ... and so should NULL values.
  ASSERT_EQ(X509_V_ERR_INVALID_CALL,
            Verify(leaf.get(), {root.get()}, {}, empty_crls, 0, false,
                   [](X509_VERIFY_PARAM *param) {
                     ASSERT_FALSE(X509_VERIFY_PARAM_set1_ip(param, nullptr, 0));
                   }));

  // Zero bytes in an IP address are, of course, fine. This is tested above
  // because |kIP| contains zeros.
}

TEST(X509Test, ZeroLengthsWithCheckFunctions) {
  bssl::UniquePtr<X509> leaf(CertFromPEM(kSANTypesLeaf));

  EXPECT_EQ(
      1, X509_check_host(leaf.get(), kHostname, strlen(kHostname), 0, nullptr));
  EXPECT_NE(1, X509_check_host(leaf.get(), kWrongHostname,
                               strlen(kWrongHostname), 0, nullptr));

  EXPECT_EQ(1, X509_check_email(leaf.get(), kEmail, strlen(kEmail), 0));
  EXPECT_NE(1,
            X509_check_email(leaf.get(), kWrongEmail, strlen(kWrongEmail), 0));

  EXPECT_EQ(1, X509_check_ip(leaf.get(), kIP, sizeof(kIP), 0));
  EXPECT_NE(1, X509_check_ip(leaf.get(), kWrongIP, sizeof(kWrongIP), 0));

  EXPECT_EQ(1, X509_check_ip_asc(leaf.get(), kIPString, 0));
  EXPECT_NE(1, X509_check_ip_asc(leaf.get(), kWrongIPString, 0));

  // OpenSSL supports passing zero as the length for host and email. We do not
  // and it should always fail.
  EXPECT_NE(1, X509_check_host(leaf.get(), kHostname, 0, 0, nullptr));
  EXPECT_NE(1, X509_check_host(leaf.get(), kWrongHostname, 0, 0, nullptr));

  EXPECT_NE(1, X509_check_email(leaf.get(), kEmail, 0, 0));
  EXPECT_NE(1, X509_check_email(leaf.get(), kWrongEmail, 0, 0));

  EXPECT_NE(1, X509_check_ip(leaf.get(), kIP, 0, 0));
  EXPECT_NE(1, X509_check_ip(leaf.get(), kWrongIP, 0, 0));

  // Unlike all the other functions, |X509_check_ip_asc| doesn't take a length,
  // so it cannot be zero.
}

TEST(X509Test, TestCRL) {
  bssl::UniquePtr<X509> root(CertFromPEM(kCRLTestRoot));
  bssl::UniquePtr<X509> leaf(CertFromPEM(kCRLTestLeaf));
  bssl::UniquePtr<X509_CRL> basic_crl(CRLFromPEM(kBasicCRL));
  bssl::UniquePtr<X509_CRL> revoked_crl(CRLFromPEM(kRevokedCRL));
  bssl::UniquePtr<X509_CRL> bad_issuer_crl(CRLFromPEM(kBadIssuerCRL));
  bssl::UniquePtr<X509_CRL> known_critical_crl(CRLFromPEM(kKnownCriticalCRL));
  bssl::UniquePtr<X509_CRL> unknown_critical_crl(
      CRLFromPEM(kUnknownCriticalCRL));
  bssl::UniquePtr<X509_CRL> unknown_critical_crl2(
      CRLFromPEM(kUnknownCriticalCRL2));

  ASSERT_TRUE(root);
  ASSERT_TRUE(leaf);
  ASSERT_TRUE(basic_crl);
  ASSERT_TRUE(revoked_crl);
  ASSERT_TRUE(bad_issuer_crl);
  ASSERT_TRUE(known_critical_crl);
  ASSERT_TRUE(unknown_critical_crl);
  ASSERT_TRUE(unknown_critical_crl2);

  ASSERT_EQ(X509_V_OK, Verify(leaf.get(), {root.get()}, {root.get()},
                              {basic_crl.get()}, X509_V_FLAG_CRL_CHECK));
  ASSERT_EQ(
      X509_V_ERR_CERT_REVOKED,
      Verify(leaf.get(), {root.get()}, {root.get()},
             {basic_crl.get(), revoked_crl.get()}, X509_V_FLAG_CRL_CHECK));

  std::vector<X509_CRL *> empty_crls;
  ASSERT_EQ(X509_V_ERR_UNABLE_TO_GET_CRL,
            Verify(leaf.get(), {root.get()}, {root.get()}, empty_crls,
                   X509_V_FLAG_CRL_CHECK));
  ASSERT_EQ(X509_V_ERR_UNABLE_TO_GET_CRL,
            Verify(leaf.get(), {root.get()}, {root.get()},
                   {bad_issuer_crl.get()}, X509_V_FLAG_CRL_CHECK));
  ASSERT_EQ(X509_V_OK,
            Verify(leaf.get(), {root.get()}, {root.get()},
                   {known_critical_crl.get()}, X509_V_FLAG_CRL_CHECK));
  ASSERT_EQ(X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION,
            Verify(leaf.get(), {root.get()}, {root.get()},
                   {unknown_critical_crl.get()}, X509_V_FLAG_CRL_CHECK));
  ASSERT_EQ(X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION,
            Verify(leaf.get(), {root.get()}, {root.get()},
                   {unknown_critical_crl2.get()}, X509_V_FLAG_CRL_CHECK));
}

TEST(X509Test, ManyNamesAndConstraints) {
  bssl::UniquePtr<X509> many_constraints(
      CertFromPEM(GetTestData("crypto/x509/many_constraints.pem").c_str()));
  ASSERT_TRUE(many_constraints);
  bssl::UniquePtr<X509> many_names1(
      CertFromPEM(GetTestData("crypto/x509/many_names1.pem").c_str()));
  ASSERT_TRUE(many_names1);
  bssl::UniquePtr<X509> many_names2(
      CertFromPEM(GetTestData("crypto/x509/many_names2.pem").c_str()));
  ASSERT_TRUE(many_names2);
  bssl::UniquePtr<X509> many_names3(
      CertFromPEM(GetTestData("crypto/x509/many_names3.pem").c_str()));
  ASSERT_TRUE(many_names3);
  bssl::UniquePtr<X509> some_names1(
      CertFromPEM(GetTestData("crypto/x509/some_names1.pem").c_str()));
  ASSERT_TRUE(some_names1);
  bssl::UniquePtr<X509> some_names2(
      CertFromPEM(GetTestData("crypto/x509/some_names2.pem").c_str()));
  ASSERT_TRUE(some_names2);
  bssl::UniquePtr<X509> some_names3(
      CertFromPEM(GetTestData("crypto/x509/some_names3.pem").c_str()));
  ASSERT_TRUE(some_names3);

  EXPECT_EQ(X509_V_ERR_UNSPECIFIED,
            Verify(many_names1.get(), {many_constraints.get()},
                   {many_constraints.get()}, {}));
  EXPECT_EQ(X509_V_ERR_UNSPECIFIED,
            Verify(many_names2.get(), {many_constraints.get()},
                   {many_constraints.get()}, {}));
  EXPECT_EQ(X509_V_ERR_UNSPECIFIED,
            Verify(many_names3.get(), {many_constraints.get()},
                   {many_constraints.get()}, {}));

  EXPECT_EQ(X509_V_OK, Verify(some_names1.get(), {many_constraints.get()},
                              {many_constraints.get()}, {}));
  EXPECT_EQ(X509_V_OK, Verify(some_names2.get(), {many_constraints.get()},
                              {many_constraints.get()}, {}));
  EXPECT_EQ(X509_V_OK, Verify(some_names3.get(), {many_constraints.get()},
                              {many_constraints.get()}, {}));
}

TEST(X509Test, TestPSS) {
  bssl::UniquePtr<X509> cert(CertFromPEM(kExamplePSSCert));
  ASSERT_TRUE(cert);

  bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(cert.get()));
  ASSERT_TRUE(pkey);

  ASSERT_TRUE(X509_verify(cert.get(), pkey.get()));
}

TEST(X509Test, TestPSSBadParameters) {
  bssl::UniquePtr<X509> cert(CertFromPEM(kBadPSSCertPEM));
  ASSERT_TRUE(cert);

  bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(cert.get()));
  ASSERT_TRUE(pkey);

  ASSERT_FALSE(X509_verify(cert.get(), pkey.get()));
  ERR_clear_error();
}

TEST(X509Test, TestEd25519) {
  bssl::UniquePtr<X509> cert(CertFromPEM(kEd25519Cert));
  ASSERT_TRUE(cert);

  bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(cert.get()));
  ASSERT_TRUE(pkey);

  ASSERT_TRUE(X509_verify(cert.get(), pkey.get()));
}

TEST(X509Test, TestEd25519BadParameters) {
  bssl::UniquePtr<X509> cert(CertFromPEM(kEd25519CertNull));
  ASSERT_TRUE(cert);

  bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(cert.get()));
  ASSERT_TRUE(pkey);

  ASSERT_FALSE(X509_verify(cert.get(), pkey.get()));

  uint32_t err = ERR_get_error();
  ASSERT_EQ(ERR_LIB_X509, ERR_GET_LIB(err));
  ASSERT_EQ(X509_R_INVALID_PARAMETER, ERR_GET_REASON(err));
  ERR_clear_error();
}

static bool SignatureRoundTrips(EVP_MD_CTX *md_ctx, EVP_PKEY *pkey) {
  // Make a certificate like signed with |md_ctx|'s settings.'
  bssl::UniquePtr<X509> cert(CertFromPEM(kLeafPEM));
  if (!cert || !X509_sign_ctx(cert.get(), md_ctx)) {
    return false;
  }

  // Ensure that |pkey| may still be used to verify the resulting signature. All
  // settings in |md_ctx| must have been serialized appropriately.
  return !!X509_verify(cert.get(), pkey);
}

TEST(X509Test, RSASign) {
  bssl::UniquePtr<EVP_PKEY> pkey(PrivateKeyFromPEM(kRSAKey));
  ASSERT_TRUE(pkey);
  // Test PKCS#1 v1.5.
  bssl::ScopedEVP_MD_CTX md_ctx;
  ASSERT_TRUE(
      EVP_DigestSignInit(md_ctx.get(), NULL, EVP_sha256(), NULL, pkey.get()));
  ASSERT_TRUE(SignatureRoundTrips(md_ctx.get(), pkey.get()));

  // Test RSA-PSS with custom parameters.
  md_ctx.Reset();
  EVP_PKEY_CTX *pkey_ctx;
  ASSERT_TRUE(EVP_DigestSignInit(md_ctx.get(), &pkey_ctx, EVP_sha256(), NULL,
                                 pkey.get()));
  ASSERT_TRUE(EVP_PKEY_CTX_set_rsa_padding(pkey_ctx, RSA_PKCS1_PSS_PADDING));
  ASSERT_TRUE(EVP_PKEY_CTX_set_rsa_mgf1_md(pkey_ctx, EVP_sha512()));
  ASSERT_TRUE(SignatureRoundTrips(md_ctx.get(), pkey.get()));
}

TEST(X509Test, Ed25519Sign) {
  uint8_t pub_bytes[32], priv_bytes[64];
  ED25519_keypair(pub_bytes, priv_bytes);

  bssl::UniquePtr<EVP_PKEY> pub(
      EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pub_bytes, 32));
  ASSERT_TRUE(pub);
  bssl::UniquePtr<EVP_PKEY> priv(
      EVP_PKEY_new_raw_private_key(EVP_PKEY_ED25519, nullptr, priv_bytes, 32));
  ASSERT_TRUE(priv);

  bssl::ScopedEVP_MD_CTX md_ctx;
  ASSERT_TRUE(
      EVP_DigestSignInit(md_ctx.get(), nullptr, nullptr, nullptr, priv.get()));
  ASSERT_TRUE(SignatureRoundTrips(md_ctx.get(), pub.get()));
}

static bool PEMToDER(bssl::UniquePtr<uint8_t> *out, size_t *out_len,
                     const char *pem) {
  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(pem, strlen(pem)));
  if (!bio) {
    return false;
  }

  char *name, *header;
  uint8_t *data;
  long data_len;
  if (!PEM_read_bio(bio.get(), &name, &header, &data, &data_len)) {
    fprintf(stderr, "failed to read PEM data.\n");
    return false;
  }
  OPENSSL_free(name);
  OPENSSL_free(header);

  out->reset(data);
  *out_len = data_len;

  return true;
}

TEST(X509Test, TestFromBuffer) {
  size_t data_len;
  bssl::UniquePtr<uint8_t> data;
  ASSERT_TRUE(PEMToDER(&data, &data_len, kRootCAPEM));

  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(data.get(), data_len, nullptr));
  ASSERT_TRUE(buf);
  bssl::UniquePtr<X509> root(X509_parse_from_buffer(buf.get()));
  ASSERT_TRUE(root);

  const uint8_t *enc_pointer = root->cert_info->enc.enc;
  const uint8_t *buf_pointer = CRYPTO_BUFFER_data(buf.get());
  ASSERT_GE(enc_pointer, buf_pointer);
  ASSERT_LT(enc_pointer, buf_pointer + CRYPTO_BUFFER_len(buf.get()));
  buf.reset();

  /* This ensures the X509 took a reference to |buf|, otherwise this will be a
   * reference to free memory and ASAN should notice. */
  ASSERT_EQ(0x30, enc_pointer[0]);
}

TEST(X509Test, TestFromBufferWithTrailingData) {
  size_t data_len;
  bssl::UniquePtr<uint8_t> data;
  ASSERT_TRUE(PEMToDER(&data, &data_len, kRootCAPEM));

  std::unique_ptr<uint8_t[]> trailing_data(new uint8_t[data_len + 1]);
  OPENSSL_memcpy(trailing_data.get(), data.get(), data_len);

  bssl::UniquePtr<CRYPTO_BUFFER> buf_trailing_data(
      CRYPTO_BUFFER_new(trailing_data.get(), data_len + 1, nullptr));
  ASSERT_TRUE(buf_trailing_data);

  bssl::UniquePtr<X509> root_trailing_data(
      X509_parse_from_buffer(buf_trailing_data.get()));
  ASSERT_FALSE(root_trailing_data);
}

TEST(X509Test, TestFromBufferModified) {
  size_t data_len;
  bssl::UniquePtr<uint8_t> data;
  ASSERT_TRUE(PEMToDER(&data, &data_len, kRootCAPEM));

  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(data.get(), data_len, nullptr));
  ASSERT_TRUE(buf);

  bssl::UniquePtr<X509> root(X509_parse_from_buffer(buf.get()));
  ASSERT_TRUE(root);

  bssl::UniquePtr<ASN1_INTEGER> fourty_two(ASN1_INTEGER_new());
  ASN1_INTEGER_set(fourty_two.get(), 42);
  X509_set_serialNumber(root.get(), fourty_two.get());

  ASSERT_EQ(static_cast<long>(data_len), i2d_X509(root.get(), nullptr));

  X509_CINF_set_modified(root->cert_info);

  ASSERT_NE(static_cast<long>(data_len), i2d_X509(root.get(), nullptr));
}

TEST(X509Test, TestFromBufferReused) {
  size_t data_len;
  bssl::UniquePtr<uint8_t> data;
  ASSERT_TRUE(PEMToDER(&data, &data_len, kRootCAPEM));

  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(data.get(), data_len, nullptr));
  ASSERT_TRUE(buf);

  bssl::UniquePtr<X509> root(X509_parse_from_buffer(buf.get()));
  ASSERT_TRUE(root);

  size_t data2_len;
  bssl::UniquePtr<uint8_t> data2;
  ASSERT_TRUE(PEMToDER(&data2, &data2_len, kLeafPEM));

  X509 *x509p = root.get();
  const uint8_t *inp = data2.get();
  X509 *ret = d2i_X509(&x509p, &inp, data2_len);
  ASSERT_EQ(root.get(), ret);
  ASSERT_EQ(nullptr, root->buf);

  // Free |data2| and ensure that |root| took its own copy. Otherwise the
  // following will trigger a use-after-free.
  data2.reset();

  uint8_t *i2d = nullptr;
  int i2d_len = i2d_X509(root.get(), &i2d);
  ASSERT_GE(i2d_len, 0);
  bssl::UniquePtr<uint8_t> i2d_storage(i2d);

  ASSERT_TRUE(PEMToDER(&data2, &data2_len, kLeafPEM));

  ASSERT_EQ(static_cast<long>(data2_len), i2d_len);
  ASSERT_EQ(0, OPENSSL_memcmp(data2.get(), i2d, i2d_len));
  ASSERT_EQ(nullptr, root->buf);
}

TEST(X509Test, TestFailedParseFromBuffer) {
  static const uint8_t kNonsense[] = {1, 2, 3, 4, 5};

  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(kNonsense, sizeof(kNonsense), nullptr));
  ASSERT_TRUE(buf);

  bssl::UniquePtr<X509> cert(X509_parse_from_buffer(buf.get()));
  ASSERT_FALSE(cert);
  ERR_clear_error();

  // Test a buffer with trailing data.
  size_t data_len;
  bssl::UniquePtr<uint8_t> data;
  ASSERT_TRUE(PEMToDER(&data, &data_len, kRootCAPEM));

  std::unique_ptr<uint8_t[]> data_with_trailing_byte(new uint8_t[data_len + 1]);
  OPENSSL_memcpy(data_with_trailing_byte.get(), data.get(), data_len);
  data_with_trailing_byte[data_len] = 0;

  bssl::UniquePtr<CRYPTO_BUFFER> buf_with_trailing_byte(
      CRYPTO_BUFFER_new(data_with_trailing_byte.get(), data_len + 1, nullptr));
  ASSERT_TRUE(buf_with_trailing_byte);

  bssl::UniquePtr<X509> root(
      X509_parse_from_buffer(buf_with_trailing_byte.get()));
  ASSERT_FALSE(root);
  ERR_clear_error();
}

TEST(X509Test, TestPrintUTCTIME) {
  static const struct {
    const char *val, *want;
  } asn1_utctime_tests[] = {
    {"", "Bad time value"},

    // Correct RFC 5280 form. Test years < 2000 and > 2000.
    {"090303125425Z", "Mar  3 12:54:25 2009 GMT"},
    {"900303125425Z", "Mar  3 12:54:25 1990 GMT"},
    {"000303125425Z", "Mar  3 12:54:25 2000 GMT"},

    // Correct form, bad values.
    {"000000000000Z", "Bad time value"},
    {"999999999999Z", "Bad time value"},

    // Missing components. Not legal RFC 5280, but permitted.
    {"090303125425", "Mar  3 12:54:25 2009"},
    {"9003031254", "Mar  3 12:54:00 1990"},
    {"9003031254Z", "Mar  3 12:54:00 1990 GMT"},

    // GENERALIZEDTIME confused for UTCTIME.
    {"20090303125425Z", "Bad time value"},

    // Legal ASN.1, but not legal RFC 5280.
    {"9003031254+0800", "Bad time value"},
    {"9003031254-0800", "Bad time value"},

    // Trailing garbage.
    {"9003031254Z ", "Bad time value"},
  };

  for (auto t : asn1_utctime_tests) {
    SCOPED_TRACE(t.val);
    bssl::UniquePtr<ASN1_UTCTIME> tm(ASN1_UTCTIME_new());
    bssl::UniquePtr<BIO> bio(BIO_new(BIO_s_mem()));

    // Use this instead of ASN1_UTCTIME_set() because some callers get
    // type-confused and pass ASN1_GENERALIZEDTIME to ASN1_UTCTIME_print().
    // ASN1_UTCTIME_set_string() is stricter, and would reject the inputs in
    // question.
    ASSERT_TRUE(ASN1_STRING_set(tm.get(), t.val, strlen(t.val)));
    const int ok = ASN1_UTCTIME_print(bio.get(), tm.get());

    const uint8_t *contents;
    size_t len;
    ASSERT_TRUE(BIO_mem_contents(bio.get(), &contents, &len));
    EXPECT_EQ(ok, (strcmp(t.want, "Bad time value") != 0) ? 1 : 0);
    EXPECT_EQ(t.want,
              std::string(reinterpret_cast<const char *>(contents), len));
  }
}

TEST(X509Test, PrettyPrintIntegers) {
  static const char *kTests[] = {
      // Small numbers are pretty-printed in decimal.
      "0",
      "-1",
      "1",
      "42",
      "-42",
      "256",
      "-256",
      // Large numbers are pretty-printed in hex to avoid taking quadratic time.
      "0x0123456789",
      "-0x0123456789",
  };
  for (const char *in : kTests) {
    SCOPED_TRACE(in);
    BIGNUM *bn = nullptr;
    ASSERT_TRUE(BN_asc2bn(&bn, in));
    bssl::UniquePtr<BIGNUM> free_bn(bn);

    {
      bssl::UniquePtr<ASN1_INTEGER> asn1(BN_to_ASN1_INTEGER(bn, nullptr));
      ASSERT_TRUE(asn1);
      bssl::UniquePtr<char> out(i2s_ASN1_INTEGER(nullptr, asn1.get()));
      ASSERT_TRUE(out.get());
      EXPECT_STREQ(in, out.get());
    }

    {
      bssl::UniquePtr<ASN1_ENUMERATED> asn1(BN_to_ASN1_ENUMERATED(bn, nullptr));
      ASSERT_TRUE(asn1);
      bssl::UniquePtr<char> out(i2s_ASN1_ENUMERATED(nullptr, asn1.get()));
      ASSERT_TRUE(out.get());
      EXPECT_STREQ(in, out.get());
    }
  }
}

TEST(X509Test, X509NameSet) {
  bssl::UniquePtr<X509_NAME> name(X509_NAME_new());
  EXPECT_TRUE(X509_NAME_add_entry_by_txt(
      name.get(), "C", MBSTRING_ASC, reinterpret_cast<const uint8_t *>("US"),
      -1, -1, 0));
  EXPECT_EQ(X509_NAME_entry_count(name.get()), 1);
  EXPECT_TRUE(X509_NAME_add_entry_by_txt(
      name.get(), "C", MBSTRING_ASC, reinterpret_cast<const uint8_t *>("CA"),
      -1, -1, 0));
  EXPECT_EQ(X509_NAME_entry_count(name.get()), 2);
  EXPECT_TRUE(X509_NAME_add_entry_by_txt(
      name.get(), "C", MBSTRING_ASC, reinterpret_cast<const uint8_t *>("UK"),
      -1, -1, 0));
  EXPECT_EQ(X509_NAME_entry_count(name.get()), 3);
  EXPECT_TRUE(X509_NAME_add_entry_by_txt(
      name.get(), "C", MBSTRING_ASC, reinterpret_cast<const uint8_t *>("JP"),
      -1, 1, 0));
  EXPECT_EQ(X509_NAME_entry_count(name.get()), 4);

  // Check that the correct entries get incremented when inserting new entry.
  EXPECT_EQ(X509_NAME_ENTRY_set(X509_NAME_get_entry(name.get(), 1)), 1);
  EXPECT_EQ(X509_NAME_ENTRY_set(X509_NAME_get_entry(name.get(), 2)), 2);
}

TEST(X509Test, StringDecoding) {
  static const struct {
    std::vector<uint8_t> in;
    int type;
    const char *expected;
  } kTests[] = {
      // Non-minimal, two-byte UTF-8.
      {{0xc0, 0x81}, V_ASN1_UTF8STRING, nullptr},
      // Non-minimal, three-byte UTF-8.
      {{0xe0, 0x80, 0x81}, V_ASN1_UTF8STRING, nullptr},
      // Non-minimal, four-byte UTF-8.
      {{0xf0, 0x80, 0x80, 0x81}, V_ASN1_UTF8STRING, nullptr},
      // Truncated, four-byte UTF-8.
      {{0xf0, 0x80, 0x80}, V_ASN1_UTF8STRING, nullptr},
      // Low-surrogate value.
      {{0xed, 0xa0, 0x80}, V_ASN1_UTF8STRING, nullptr},
      // High-surrogate value.
      {{0xed, 0xb0, 0x81}, V_ASN1_UTF8STRING, nullptr},
      // Initial BOMs should be rejected from UCS-2 and UCS-4.
      {{0xfe, 0xff, 0, 88}, V_ASN1_BMPSTRING, nullptr},
      {{0, 0, 0xfe, 0xff, 0, 0, 0, 88}, V_ASN1_UNIVERSALSTRING, nullptr},
      // Otherwise, BOMs should pass through.
      {{0, 88, 0xfe, 0xff}, V_ASN1_BMPSTRING, "X\xef\xbb\xbf"},
      {{0, 0, 0, 88, 0, 0, 0xfe, 0xff}, V_ASN1_UNIVERSALSTRING,
       "X\xef\xbb\xbf"},
      // The maximum code-point should pass though.
      {{0, 16, 0xff, 0xfd}, V_ASN1_UNIVERSALSTRING, "\xf4\x8f\xbf\xbd"},
      // Values outside the Unicode space should not.
      {{0, 17, 0, 0}, V_ASN1_UNIVERSALSTRING, nullptr},
      // Non-characters should be rejected.
      {{0, 1, 0xff, 0xff}, V_ASN1_UNIVERSALSTRING, nullptr},
      {{0, 1, 0xff, 0xfe}, V_ASN1_UNIVERSALSTRING, nullptr},
      {{0, 0, 0xfd, 0xd5}, V_ASN1_UNIVERSALSTRING, nullptr},
      // BMPString is UCS-2, not UTF-16, so surrogate pairs are invalid.
      {{0xd8, 0, 0xdc, 1}, V_ASN1_BMPSTRING, nullptr},
  };

  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kTests); i++) {
    SCOPED_TRACE(i);
    const auto& test = kTests[i];
    ASN1_STRING s;
    s.type = test.type;
    s.data = const_cast<uint8_t*>(test.in.data());
    s.length = test.in.size();

    uint8_t *utf8;
    const int utf8_len = ASN1_STRING_to_UTF8(&utf8, &s);
    EXPECT_EQ(utf8_len < 0, test.expected == nullptr);
    if (utf8_len >= 0) {
      if (test.expected != nullptr) {
        EXPECT_EQ(Bytes(test.expected), Bytes(utf8, utf8_len));
      }
      OPENSSL_free(utf8);
    } else {
      ERR_clear_error();
    }
  }
}

TEST(X509Test, NoBasicConstraintsCertSign) {
  bssl::UniquePtr<X509> root(CertFromPEM(kSANTypesRoot));
  bssl::UniquePtr<X509> intermediate(
      CertFromPEM(kNoBasicConstraintsCertSignIntermediate));
  bssl::UniquePtr<X509> leaf(CertFromPEM(kNoBasicConstraintsCertSignLeaf));

  ASSERT_TRUE(root);
  ASSERT_TRUE(intermediate);
  ASSERT_TRUE(leaf);

  // The intermediate has keyUsage certSign, but is not marked as a CA in the
  // basicConstraints.
  EXPECT_EQ(X509_V_ERR_INVALID_CA,
            Verify(leaf.get(), {root.get()}, {intermediate.get()}, {}, 0));
}

TEST(X509Test, NoBasicConstraintsNetscapeCA) {
  bssl::UniquePtr<X509> root(CertFromPEM(kSANTypesRoot));
  bssl::UniquePtr<X509> intermediate(
      CertFromPEM(kNoBasicConstraintsNetscapeCAIntermediate));
  bssl::UniquePtr<X509> leaf(CertFromPEM(kNoBasicConstraintsNetscapeCALeaf));

  ASSERT_TRUE(root);
  ASSERT_TRUE(intermediate);
  ASSERT_TRUE(leaf);

  // The intermediate has a Netscape certificate type of "SSL CA", but is not
  // marked as a CA in the basicConstraints.
  EXPECT_EQ(X509_V_ERR_INVALID_CA,
            Verify(leaf.get(), {root.get()}, {intermediate.get()}, {}, 0));
}

TEST(X509Test, MismatchAlgorithms) {
  bssl::UniquePtr<X509> cert(CertFromPEM(kSelfSignedMismatchAlgorithms));
  ASSERT_TRUE(cert);

  bssl::UniquePtr<EVP_PKEY> pkey(X509_get_pubkey(cert.get()));
  ASSERT_TRUE(pkey);

  EXPECT_FALSE(X509_verify(cert.get(), pkey.get()));
  uint32_t err = ERR_get_error();
  EXPECT_EQ(ERR_LIB_X509, ERR_GET_LIB(err));
  EXPECT_EQ(X509_R_SIGNATURE_ALGORITHM_MISMATCH, ERR_GET_REASON(err));
}

TEST(X509Test, PEMX509Info) {
  std::string cert = kRootCAPEM;
  auto cert_obj = CertFromPEM(kRootCAPEM);
  ASSERT_TRUE(cert_obj);

  std::string rsa = kRSAKey;
  auto rsa_obj = PrivateKeyFromPEM(kRSAKey);
  ASSERT_TRUE(rsa_obj);

  std::string crl = kBasicCRL;
  auto crl_obj = CRLFromPEM(kBasicCRL);
  ASSERT_TRUE(crl_obj);

  std::string unknown =
      "-----BEGIN UNKNOWN-----\n"
      "AAAA\n"
      "-----END UNKNOWN-----\n";

  std::string invalid =
      "-----BEGIN CERTIFICATE-----\n"
      "AAAA\n"
      "-----END CERTIFICATE-----\n";

  // Each X509_INFO contains at most one certificate, CRL, etc. The format
  // creates a new X509_INFO when a repeated type is seen.
  std::string pem =
      // The first few entries have one of everything in different orders.
      cert + rsa + crl +
      rsa + crl + cert +
      // Unknown types are ignored.
      crl + unknown + cert + rsa +
      // Seeing a new certificate starts a new entry, so now we have a bunch of
      // certificate-only entries.
      cert + cert + cert +
      // The key folds into the certificate's entry.
      cert + rsa +
      // Doubled keys also start new entries.
      rsa + rsa + rsa + rsa + crl +
      // As do CRLs.
      crl + crl;

  const struct ExpectedInfo {
    const X509 *cert;
    const EVP_PKEY *key;
    const X509_CRL *crl;
  } kExpected[] = {
    {cert_obj.get(), rsa_obj.get(), crl_obj.get()},
    {cert_obj.get(), rsa_obj.get(), crl_obj.get()},
    {cert_obj.get(), rsa_obj.get(), crl_obj.get()},
    {cert_obj.get(), nullptr, nullptr},
    {cert_obj.get(), nullptr, nullptr},
    {cert_obj.get(), nullptr, nullptr},
    {cert_obj.get(), rsa_obj.get(), nullptr},
    {nullptr, rsa_obj.get(), nullptr},
    {nullptr, rsa_obj.get(), nullptr},
    {nullptr, rsa_obj.get(), nullptr},
    {nullptr, rsa_obj.get(), crl_obj.get()},
    {nullptr, nullptr, crl_obj.get()},
    {nullptr, nullptr, crl_obj.get()},
  };

  auto check_info = [](const ExpectedInfo *expected, const X509_INFO *info) {
    if (expected->cert != nullptr) {
      EXPECT_EQ(0, X509_cmp(expected->cert, info->x509));
    } else {
      EXPECT_EQ(nullptr, info->x509);
    }
    if (expected->crl != nullptr) {
      EXPECT_EQ(0, X509_CRL_cmp(expected->crl, info->crl));
    } else {
      EXPECT_EQ(nullptr, info->crl);
    }
    if (expected->key != nullptr) {
      ASSERT_NE(nullptr, info->x_pkey);
      // EVP_PKEY_cmp returns one if the keys are equal.
      EXPECT_EQ(1, EVP_PKEY_cmp(expected->key, info->x_pkey->dec_pkey));
    } else {
      EXPECT_EQ(nullptr, info->x_pkey);
    }
  };

  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(pem.data(), pem.size()));
  ASSERT_TRUE(bio);
  bssl::UniquePtr<STACK_OF(X509_INFO)> infos(
      PEM_X509_INFO_read_bio(bio.get(), nullptr, nullptr, nullptr));
  ASSERT_TRUE(infos);
  ASSERT_EQ(OPENSSL_ARRAY_SIZE(kExpected), sk_X509_INFO_num(infos.get()));
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kExpected); i++) {
    SCOPED_TRACE(i);
    check_info(&kExpected[i], sk_X509_INFO_value(infos.get(), i));
  }

  // Passing an existing stack appends to it.
  bio.reset(BIO_new_mem_buf(pem.data(), pem.size()));
  ASSERT_TRUE(bio);
  ASSERT_EQ(infos.get(),
            PEM_X509_INFO_read_bio(bio.get(), infos.get(), nullptr, nullptr));
  ASSERT_EQ(2 * OPENSSL_ARRAY_SIZE(kExpected), sk_X509_INFO_num(infos.get()));
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kExpected); i++) {
    SCOPED_TRACE(i);
    check_info(&kExpected[i], sk_X509_INFO_value(infos.get(), i));
    check_info(
        &kExpected[i],
        sk_X509_INFO_value(infos.get(), i + OPENSSL_ARRAY_SIZE(kExpected)));
  }

  // Gracefully handle errors in both the append and fresh cases.
  std::string bad_pem = cert + cert + invalid;

  bio.reset(BIO_new_mem_buf(bad_pem.data(), bad_pem.size()));
  ASSERT_TRUE(bio);
  bssl::UniquePtr<STACK_OF(X509_INFO)> infos2(
      PEM_X509_INFO_read_bio(bio.get(), nullptr, nullptr, nullptr));
  EXPECT_FALSE(infos2);

  bio.reset(BIO_new_mem_buf(bad_pem.data(), bad_pem.size()));
  ASSERT_TRUE(bio);
  EXPECT_FALSE(
      PEM_X509_INFO_read_bio(bio.get(), infos.get(), nullptr, nullptr));
  EXPECT_EQ(2 * OPENSSL_ARRAY_SIZE(kExpected), sk_X509_INFO_num(infos.get()));
}

TEST(X509Test, ReadBIOEmpty) {
  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(nullptr, 0));
  ASSERT_TRUE(bio);

  // CPython expects |ASN1_R_HEADER_TOO_LONG| on EOF, to terminate a series of
  // certificates.
  bssl::UniquePtr<X509> x509(d2i_X509_bio(bio.get(), nullptr));
  EXPECT_FALSE(x509);
  uint32_t err = ERR_get_error();
  EXPECT_EQ(ERR_LIB_ASN1, ERR_GET_LIB(err));
  EXPECT_EQ(ASN1_R_HEADER_TOO_LONG, ERR_GET_REASON(err));
}

TEST(X509Test, ReadBIOOneByte) {
  bssl::UniquePtr<BIO> bio(BIO_new_mem_buf("\x30", 1));
  ASSERT_TRUE(bio);

  // CPython expects |ASN1_R_HEADER_TOO_LONG| on EOF, to terminate a series of
  // certificates. This EOF appeared after some data, however, so we do not wish
  // to signal EOF.
  bssl::UniquePtr<X509> x509(d2i_X509_bio(bio.get(), nullptr));
  EXPECT_FALSE(x509);
  uint32_t err = ERR_get_error();
  EXPECT_EQ(ERR_LIB_ASN1, ERR_GET_LIB(err));
  EXPECT_EQ(ASN1_R_NOT_ENOUGH_DATA, ERR_GET_REASON(err));
}

TEST(X509Test, PartialBIOReturn) {
  // Create a filter BIO that only reads and writes one byte at a time.
  bssl::UniquePtr<BIO_METHOD> method(BIO_meth_new(0, nullptr));
  ASSERT_TRUE(method);
  ASSERT_TRUE(BIO_meth_set_create(method.get(), [](BIO *b) -> int {
    BIO_set_init(b, 1);
    return 1;
  }));
  ASSERT_TRUE(
      BIO_meth_set_read(method.get(), [](BIO *b, char *out, int len) -> int {
        return BIO_read(BIO_next(b), out, std::min(len, 1));
      }));
  ASSERT_TRUE(BIO_meth_set_write(
      method.get(), [](BIO *b, const char *in, int len) -> int {
        return BIO_write(BIO_next(b), in, std::min(len, 1));
      }));

  bssl::UniquePtr<BIO> bio(BIO_new(method.get()));
  ASSERT_TRUE(bio);
  BIO *mem_bio = BIO_new(BIO_s_mem());
  ASSERT_TRUE(mem_bio);
  BIO_push(bio.get(), mem_bio);  // BIO_push takes ownership.

  bssl::UniquePtr<X509> cert(CertFromPEM(kLeafPEM));
  ASSERT_TRUE(cert);
  uint8_t *der = nullptr;
  int der_len = i2d_X509(cert.get(), &der);
  ASSERT_GT(der_len, 0);
  bssl::UniquePtr<uint8_t> free_der(der);

  // Write the certificate into the BIO. Though we only write one byte at a
  // time, the write should succeed.
  ASSERT_EQ(1, i2d_X509_bio(bio.get(), cert.get()));
  const uint8_t *der2;
  size_t der2_len;
  ASSERT_TRUE(BIO_mem_contents(mem_bio, &der2, &der2_len));
  EXPECT_EQ(Bytes(der, static_cast<size_t>(der_len)), Bytes(der2, der2_len));

  // Read the certificate back out of the BIO. Though we only read one byte at a
  // time, the read should succeed.
  bssl::UniquePtr<X509> cert2(d2i_X509_bio(bio.get(), nullptr));
  ASSERT_TRUE(cert2);
  EXPECT_EQ(0, X509_cmp(cert.get(), cert2.get()));
}

TEST(X509Test, CommonNameFallback) {
  bssl::UniquePtr<X509> root = CertFromPEM(kSANTypesRoot);
  ASSERT_TRUE(root);
  bssl::UniquePtr<X509> with_sans = CertFromPEM(kCommonNameWithSANs);
  ASSERT_TRUE(with_sans);
  bssl::UniquePtr<X509> without_sans = CertFromPEM(kCommonNameWithoutSANs);
  ASSERT_TRUE(without_sans);
  bssl::UniquePtr<X509> with_email = CertFromPEM(kCommonNameWithEmailSAN);
  ASSERT_TRUE(with_email);
  bssl::UniquePtr<X509> with_ip = CertFromPEM(kCommonNameWithIPSAN);
  ASSERT_TRUE(with_ip);

  auto verify_cert = [&](X509 *leaf, unsigned flags, const char *host) {
    return Verify(
        leaf, {root.get()}, {}, {}, 0, false, [&](X509_VERIFY_PARAM *param) {
          ASSERT_TRUE(X509_VERIFY_PARAM_set1_host(param, host, strlen(host)));
          X509_VERIFY_PARAM_set_hostflags(param, flags);
        });
  };

  // By default, the common name is ignored if the SAN list is present but
  // otherwise is checked.
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_sans.get(), 0 /* no flags */, "foo.host1.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), 0 /* no flags */, "foo.host2.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), 0 /* no flags */, "foo.host3.test"));
  EXPECT_EQ(X509_V_OK, verify_cert(without_sans.get(), 0 /* no flags */,
                                   "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_email.get(), 0 /* no flags */, "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_ip.get(), 0 /* no flags */, "foo.host1.test"));

  // X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT is ignored.
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                        "foo.host1.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                        "foo.host2.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                        "foo.host3.test"));
  EXPECT_EQ(X509_V_OK, verify_cert(without_sans.get(),
                                   X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                                   "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_email.get(), X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                        "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_ip.get(), X509_CHECK_FLAG_ALWAYS_CHECK_SUBJECT,
                        "foo.host1.test"));

  // X509_CHECK_FLAG_NEVER_CHECK_SUBJECT implements the correct behavior: the
  // common name is never checked.
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host1.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host2.test"));
  EXPECT_EQ(X509_V_OK,
            verify_cert(with_sans.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host3.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(without_sans.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_email.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host1.test"));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(with_ip.get(), X509_CHECK_FLAG_NEVER_CHECK_SUBJECT,
                        "foo.host1.test"));
}

TEST(X509Test, LooksLikeDNSName) {
    static const char *kValid[] = {
        "example.com",
        "eXample123-.com",
        "*.example.com",
        "exa_mple.com",
        "example.com.",
        "project-dev:us-central1:main",
    };
    static const char *kInvalid[] = {
        "-eXample123-.com",
        "",
        ".",
        "*",
        "*.",
        "example..com",
        ".example.com",
        "example.com..",
        "*foo.example.com",
        "foo.*.example.com",
        "foo,bar",
    };

    for (const char *str : kValid) {
      SCOPED_TRACE(str);
      EXPECT_TRUE(x509v3_looks_like_dns_name(
          reinterpret_cast<const uint8_t *>(str), strlen(str)));
    }
    for (const char *str : kInvalid) {
      SCOPED_TRACE(str);
      EXPECT_FALSE(x509v3_looks_like_dns_name(
          reinterpret_cast<const uint8_t *>(str), strlen(str)));
    }
}

TEST(X509Test, CommonNameAndNameConstraints) {
  bssl::UniquePtr<X509> root = CertFromPEM(kSANTypesRoot);
  ASSERT_TRUE(root);
  bssl::UniquePtr<X509> intermediate = CertFromPEM(kConstrainedIntermediate);
  ASSERT_TRUE(intermediate);
  bssl::UniquePtr<X509> permitted = CertFromPEM(kCommonNamePermittedLeaf);
  ASSERT_TRUE(permitted);
  bssl::UniquePtr<X509> not_permitted =
      CertFromPEM(kCommonNameNotPermittedLeaf);
  ASSERT_TRUE(not_permitted);
  bssl::UniquePtr<X509> not_permitted_with_sans =
      CertFromPEM(kCommonNameNotPermittedWithSANsLeaf);
  ASSERT_TRUE(not_permitted_with_sans);
  bssl::UniquePtr<X509> not_dns = CertFromPEM(kCommonNameNotDNSLeaf);
  ASSERT_TRUE(not_dns);

  auto verify_cert = [&](X509 *leaf, unsigned flags, const char *host) {
    return Verify(
        leaf, {root.get()}, {intermediate.get()}, {}, 0, false,
        [&](X509_VERIFY_PARAM *param) {
          ASSERT_TRUE(X509_VERIFY_PARAM_set1_host(param, host, strlen(host)));
          X509_VERIFY_PARAM_set_hostflags(param, flags);
        });
  };

  // Certificates which would otherwise trigger the common name fallback are
  // rejected whenever there are name constraints. We do this whether or not
  // the common name matches the constraints.
  EXPECT_EQ(
      X509_V_ERR_NAME_CONSTRAINTS_WITHOUT_SANS,
      verify_cert(permitted.get(), 0 /* no flags */, kCommonNamePermitted));
  EXPECT_EQ(X509_V_ERR_NAME_CONSTRAINTS_WITHOUT_SANS,
            verify_cert(not_permitted.get(), 0 /* no flags */,
                        kCommonNameNotPermitted));

  // This occurs even if the built-in name checks aren't used. The caller may
  // separately call |X509_check_host|.
  EXPECT_EQ(X509_V_ERR_NAME_CONSTRAINTS_WITHOUT_SANS,
            Verify(not_permitted.get(), {root.get()}, {intermediate.get()}, {},
                   0 /* no flags */, false, nullptr));

  // If the leaf certificate has SANs, the common name fallback is always
  // disabled, so the name constraints do not apply.
  EXPECT_EQ(X509_V_OK, Verify(not_permitted_with_sans.get(), {root.get()},
                              {intermediate.get()}, {}, 0, false, nullptr));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(not_permitted_with_sans.get(), 0 /* no flags */,
                        kCommonNameNotPermittedWithSANs));

  // If the common name does not look like a DNS name, we apply neither name
  // constraints nor common name fallback.
  EXPECT_EQ(X509_V_OK, Verify(not_dns.get(), {root.get()}, {intermediate.get()},
                              {}, 0, false, nullptr));
  EXPECT_EQ(X509_V_ERR_HOSTNAME_MISMATCH,
            verify_cert(not_dns.get(), 0 /* no flags */, kCommonNameNotDNS));
}

TEST(X509Test, ServerGatedCryptoEKUs) {
  bssl::UniquePtr<X509> root = CertFromPEM(kSANTypesRoot);
  ASSERT_TRUE(root);
  bssl::UniquePtr<X509> ms_sgc = CertFromPEM(kMicrosoftSGCCert);
  ASSERT_TRUE(ms_sgc);
  bssl::UniquePtr<X509> ns_sgc = CertFromPEM(kNetscapeSGCCert);
  ASSERT_TRUE(ns_sgc);
  bssl::UniquePtr<X509> server_eku = CertFromPEM(kServerEKUCert);
  ASSERT_TRUE(server_eku);
  bssl::UniquePtr<X509> server_eku_plus_ms_sgc =
      CertFromPEM(kServerEKUPlusMicrosoftSGCCert);
  ASSERT_TRUE(server_eku_plus_ms_sgc);
  bssl::UniquePtr<X509> any_eku = CertFromPEM(kAnyEKU);
  ASSERT_TRUE(any_eku);
  bssl::UniquePtr<X509> no_eku = CertFromPEM(kNoEKU);
  ASSERT_TRUE(no_eku);

  auto verify_cert = [&root](X509 *leaf) {
    return Verify(leaf, {root.get()}, /*intermediates=*/{}, /*crls=*/{},
                  /*flags=*/0, /*use_additional_untrusted=*/false,
                  [&](X509_VERIFY_PARAM *param) {
                    ASSERT_TRUE(X509_VERIFY_PARAM_set_purpose(
                        param, X509_PURPOSE_SSL_SERVER));
                  });
  };

  // Neither the Microsoft nor Netscape SGC EKU should be sufficient for
  // |X509_PURPOSE_SSL_SERVER|. The "any" EKU probably, technically, should be.
  // However, we've never accepted it and it's not acceptable in leaf
  // certificates by the Baseline, so perhaps we don't need this complexity.
  for (X509 *leaf : {ms_sgc.get(), ns_sgc.get(), any_eku.get()}) {
    EXPECT_EQ(X509_V_ERR_INVALID_PURPOSE, verify_cert(leaf));
  }

  // The server-auth EKU is sufficient, and it doesn't matter if an SGC EKU is
  // also included. Lastly, not specifying an EKU is also valid.
  for (X509 *leaf : {server_eku.get(), server_eku_plus_ms_sgc.get(),
                     no_eku.get()}) {
    EXPECT_EQ(X509_V_OK, verify_cert(leaf));
  }
}
