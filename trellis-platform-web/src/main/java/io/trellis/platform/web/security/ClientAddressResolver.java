package io.trellis.platform.web.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

public final class ClientAddressResolver {
  private static final String FORWARDED_FOR = "X-Forwarded-For";
  private final List<IpAddressMatcher> trusted;

  public ClientAddressResolver(String proxies) {
    trusted =
        Arrays.stream(proxies.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
  }

  public String resolve(HttpServletRequest request) {
    String address = canonical(request.getRemoteAddr());
    String header = request.getHeader(FORWARDED_FOR);
    if (header == null || !isTrusted(address)) return address;
    String[] chain = header.split(",", -1);
    for (int i = chain.length - 1; i >= 0 && isTrusted(address); i--)
      address = canonical(chain[i].trim());
    return address;
  }

  private boolean isTrusted(String address) {
    return trusted.stream().anyMatch(proxy -> proxy.matches(address));
  }

  private String canonical(String address) {
    if (address == null
        || !(address.contains(":")
            ? address.matches("[0-9a-fA-F:.]+")
            : address.matches("[0-9]+(\\.[0-9]+){3}")))
      throw new IllegalArgumentException("Invalid client address");
    try {
      return InetAddress.getByName(address).getHostAddress();
    } catch (UnknownHostException error) {
      throw new IllegalArgumentException("Invalid client address", error);
    }
  }
}
