export const toMap = <T, K>(array: T[], keyExtractor: (item: T) => K): Map<K, T> => {
  const map = new Map<K, T>();
  for (const item of array) {
    const key = keyExtractor(item);
    map.set(key, item);
  }
  return map;
}

export const checkResult = (res: any) => {
  if (res.errors && res.errors.length > 0) {
    throw res
  }
}
