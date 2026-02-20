let tokenValue = "";

export function getToken() {
  return tokenValue;
}

export function setToken(token) {
  tokenValue = token || "";
}

export function clearToken() {
  tokenValue = "";
}
