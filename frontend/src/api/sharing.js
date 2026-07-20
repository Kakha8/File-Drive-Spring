import { apiFetch } from "./http";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.clone().json();
        return data.message || data.error || fallbackMessage;
    } catch {
        try {
            const text = await response.text();
            return text || fallbackMessage;
        } catch {
            return fallbackMessage;
        }
    }
}

async function shareWithUser({
                                 fileIds,
                                 folderIds,
                                 username,
                                 role,
                             }) {
    const response = await apiFetch("/api/share", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds,
            folderIds,
            targetUsername: username,
            role: role || "VIEWER",
        }),
    });

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to share selected items"
            )
        );
    }

    return response.json();
}

export async function shareResource(payload) {
    const shares = payload.shares || [];
    const fileIds = payload.fileIds || [];
    const folderIds = payload.folderIds || [];

    if (shares.length === 0) {
        return [];
    }

    if (fileIds.length === 0 && folderIds.length === 0) {
        throw new Error("No files or folders selected");
    }

    return Promise.all(
        shares.map((share) =>
            shareWithUser({
                fileIds,
                folderIds,
                username: share.username,
                role: share.role,
            })
        )
    );
}

export async function getSharedWithMe() {
    const response = await apiFetch("/api/share/with-me");

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load shared items"
            )
        );
    }

    return response.json();
}

export async function getResourceShares(
    resourceType,
    resourceId
) {
    let path;

    if (resourceType === "FILE") {
        path = `/api/share/files/${resourceId}`;
    } else if (resourceType === "FOLDER") {
        path = `/api/share/folders/${resourceId}`;
    } else {
        throw new Error(
            `Invalid resource type: ${resourceType}`
        );
    }

    const response = await apiFetch(path);

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load current shares"
            )
        );
    }

    return response.json();
}

export async function revokeShare(shareId) {
    const response = await apiFetch(
        `/api/share/${shareId}`,
        {
            method: "DELETE",
        }
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to remove share"
            )
        );
    }
}