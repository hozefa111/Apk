# AndroidX Media3 includes required consumer ProGuard rules.
# Keep custom rules minimal to maximize shrinking and avoid hiding problems.

# SMBJ transitively includes MBassador's optional Expression Language filtering.
# This optional filter is never used by Orbit's SMB read/browse path.
-dontwarn javax.el.**

# SMBJ chooses NTLM authentication factories dynamically on Android.
-keep class com.hierynomus.smbj.auth.NtlmAuthenticator$Factory { *; }

# SMBJ excludes Kerberos/SPNEGO on Android and uses NTLM instead; GSS classes are JRE-only.
-dontwarn org.ietf.jgss.**
