// Use the nearest deleted ancestor as the display parent. Original folders
// that are still in Drive must not hide independently deleted items.
export function trashDisplayParent(originalParent, deletedFolders, rootPrefix) {
    let parent = rootPrefix;
    let longestMatch = 0;

    for (const folder of deletedFolders) {
        if (!folder.prefix) continue;
        const prefix = folder.prefix.endsWith("/")
            ? folder.prefix
            : `${folder.prefix}/`;
        if (originalParent.startsWith(prefix) && prefix.length > longestMatch) {
            parent = prefix;
            longestMatch = prefix.length;
        }
    }

    return parent;
}
