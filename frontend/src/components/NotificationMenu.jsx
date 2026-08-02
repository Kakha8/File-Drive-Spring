import {
    useCallback,
    useEffect,
    useId,
    useRef,
    useState,
} from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
    getNotifications,
    getUnreadNotificationCount,
    markAllNotificationsRead,
    markNotificationRead,
} from "../api/notifications";
import { getRecentActivity } from "../api/activity";
import ActivityHistoryModal from "./ActivityHistoryModal";

const NOTIFICATIONS_CHANGED_EVENT = "file-drive:notifications-changed";

function Icon({ children, className = "" }) {
    return (
        <svg
            className={className}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
        >
            {children}
        </svg>
    );
}

function BellIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
            <path d="M10 21h4" />
        </Icon>
    );
}

function ActivityIcon({ className }) {
    return (
        <Icon className={className}>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v3" />
            <path d="M6.5 14h2.2l1.5-2.7 2.3 5.2 1.5-2.5h3.5" />
        </Icon>
    );
}

function RestoreIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M3 7v5h5" />
            <path d="M5.7 17.3a8 8 0 1 0-.7-9.6L3 12" />
            <path d="M12 8v4l2.5 1.5" />
        </Icon>
    );
}

function TrashIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M3 6h18" />
            <path d="M8 6V4h8v2" />
            <path d="M6 6l1 15h10l1-15" />
        </Icon>
    );
}

function FolderPlusIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M3 7h6l2 2h10v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z" />
            <path d="M12 12v5" />
            <path d="M9.5 14.5h5" />
        </Icon>
    );
}

function EditIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z" />
        </Icon>
    );
}

function CopyIcon({ className }) {
    return (
        <Icon className={className}>
            <rect x="8" y="8" width="12" height="12" rx="2" />
            <path d="M4 16V6a2 2 0 0 1 2-2h10" />
        </Icon>
    );
}

function MoveIcon({ className }) {
    return (
        <Icon className={className}>
            <circle cx="6" cy="6" r="2" />
            <circle cx="6" cy="18" r="2" />
            <path d="M20 4 8 16" />
            <path d="M8 8l12 12" />
        </Icon>
    );
}

function StarIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M12 3.5 14.7 9l6 .9-4.35 4.25 1.05 6L12 17.3l-5.4 2.85 1.05-6L3.3 9.9l6-.9z" />
        </Icon>
    );
}

function UploadIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M12 16V4" />
            <path d="m7 9 5-5 5 5" />
            <path d="M4 20h16" />
        </Icon>
    );
}

function ShareIcon({ className }) {
    return (
        <Icon className={className}>
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <path d="M8.6 10.7 15.4 6.3" />
            <path d="M8.6 13.3 15.4 17.7" />
        </Icon>
    );
}

function WarningIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M10.3 4.3 2.6 18a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0Z" />
            <path d="M12 9v4" />
            <path d="M12 17h.01" />
        </Icon>
    );
}

function LockIcon({ className }) {
    return (
        <Icon className={className}>
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
        </Icon>
    );
}

function CloseIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="m6 6 12 12" />
            <path d="m18 6-12 12" />
        </Icon>
    );
}

function ArrowRightIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M5 12h14" />
            <path d="m13 6 6 6-6 6" />
        </Icon>
    );
}

function NotificationTypeIcon({
                                  type,
                                  className = "notification-type-svg",
                              }) {
    if (
        type === "UPLOAD_COMPLETED" ||
        type === "UPLOAD_FAILED"
    ) {
        return <UploadIcon className={className} />;
    }

    if (
        type === "FILE_SHARED" ||
        type === "FOLDER_SHARED" ||
        type === "PERMISSION_CHANGED"
    ) {
        return <ShareIcon className={className} />;
    }

    if (type === "MALWARE_DETECTED") {
        return <WarningIcon className={className} />;
    }

    if (type === "ACCESS_REVOKED") {
        return <LockIcon className={className} />;
    }

    return <BellIcon className={className} />;
}

function getTypeClass(type) {
    return `type-${String(type || "notification")
        .toLowerCase()
        .replaceAll("_", "-")}`;
}

function getTypeLabel(type) {
    const labels = {
        FILE_SHARED: "Shared with you",
        FOLDER_SHARED: "Shared with you",
        PERMISSION_CHANGED: "Permission updated",
        ACCESS_REVOKED: "Access update",
        UPLOAD_COMPLETED: "Upload",
        UPLOAD_FAILED: "Upload",
        MALWARE_DETECTED: "Security alert",
        STORAGE_WARNING: "Storage",
        ITEM_RESTORED: "Restored item",
        ITEM_PERMANENTLY_DELETED: "Deleted item",
    };

    return labels[type] || "Notification";
}

function formatActivityType(type) {
    if (!type) {
        return "Activity";
    }

    return String(type)
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/\b\w/g, (letter) =>
            letter.toUpperCase()
        );
}

function getActivityAction(activity) {
    const candidates = [
        activity?.type,
        activity?.action,
        activity?.title,
    ]
        .filter(Boolean)
        .map((value) => String(value).toUpperCase());

    return candidates.find((value) =>
        [
            "RESTORE",
            "DELETE",
            "TRASH",
            "UPLOAD",
            "CREATE",
            "RENAME",
            "COPY",
            "MOVE",
            "FAVORITE",
        ].some((action) => value.includes(action))
    ) || candidates[0] || "";
}

function ActivityTypeIcon({
                              type,
                              className = "notification-activity-item-svg",
                          }) {
    const value = String(type || "").toUpperCase();

    if (value.includes("RESTORE")) {
        return <RestoreIcon className={className} />;
    }

    if (
        value.includes("PERMANENT") &&
        value.includes("DELETE")
    ) {
        return <TrashIcon className={className} />;
    }

    if (
        value.includes("TRASH") ||
        value === "DELETE"
    ) {
        return <TrashIcon className={className} />;
    }

    if (value.includes("UPLOAD")) {
        return <UploadIcon className={className} />;
    }

    if (
        value.includes("CREATE") &&
        value.includes("FOLDER")
    ) {
        return <FolderPlusIcon className={className} />;
    }

    if (value.includes("RENAME")) {
        return <EditIcon className={className} />;
    }

    if (value.includes("COPY")) {
        return <CopyIcon className={className} />;
    }

    if (value.includes("MOVE")) {
        return <MoveIcon className={className} />;
    }

    if (value.includes("FAVORITE")) {
        return <StarIcon className={className} />;
    }

    return <ActivityIcon className={className} />;
}

function getActivityTypeClass(type) {
    return `activity-${String(type || "activity")
        .toLowerCase()
        .replaceAll("_", "-")}`;
}

function getActivityModalDetails(activity) {
    const details = [
        {
            label: "Item",
            value: activity.resourceName,
        },
        {
            label: "Item type",
            value: activity.entityType
                ? formatActivityType(activity.entityType)
                : null,
        },
        ...(Array.isArray(activity.details)
            ? activity.details
            : []),
        {
            label: "Occurred",
            value: activity.createdAt,
        },
        {
            label: "Performed by",
            value: "You",
        },
    ];

    return details.filter(
        (detail) =>
            detail?.value !== null &&
            detail?.value !== undefined &&
            detail?.value !== ""
    );
}

function parseDate(value) {
    if (!value) {
        return null;
    }

    const date = new Date(value);

    return Number.isNaN(date.getTime())
        ? null
        : date;
}

function formatActivityDetailValue(detail) {
    const value = detail?.value;

    if (
        value === null ||
        value === undefined ||
        value === ""
    ) {
        return "";
    }

    const label = String(
        detail?.label || ""
    ).toLowerCase();

    const looksLikeIsoTimestamp =
        typeof value === "string" &&
        /^\d{4}-\d{2}-\d{2}T/.test(value);

    const isDateDetail =
        looksLikeIsoTimestamp ||
        label.includes("date") ||
        label.includes("time") ||
        label.includes("occurred") ||
        label.includes("deleted") ||
        label.includes("restored") ||
        label.includes("uploaded") ||
        label.includes("moved to trash") ||
        label.includes("created");

    if (isDateDetail && parseDate(value)) {
        return formatFullDate(value);
    }

    return value;
}

function formatRelativeTime(value) {
    const date = parseDate(value);

    if (!date) {
        return "";
    }

    const differenceSeconds = Math.round(
        (date.getTime() - Date.now()) / 1000
    );

    const formatter = new Intl.RelativeTimeFormat(
        undefined,
        { numeric: "auto" }
    );

    const ranges = [
        ["year", 31_536_000],
        ["month", 2_592_000],
        ["week", 604_800],
        ["day", 86_400],
        ["hour", 3_600],
        ["minute", 60],
    ];

    for (const [unit, seconds] of ranges) {
        if (Math.abs(differenceSeconds) >= seconds) {
            return formatter.format(
                Math.round(differenceSeconds / seconds),
                unit
            );
        }
    }

    return "just now";
}

function formatFullDate(value) {
    const date = parseDate(value);

    if (!date) {
        return "—";
    }

    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(date);
}

function formatBytes(bytes) {
    const value = Number(bytes);

    if (!Number.isFinite(value) || value < 0) {
        return null;
    }

    if (value === 0) {
        return "0 B";
    }

    const units = ["B", "KB", "MB", "GB", "TB"];
    const index = Math.min(
        Math.floor(Math.log(value) / Math.log(1024)),
        units.length - 1
    );

    const formatted = value / 1024 ** index;

    return `${formatted.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function getFriendlyFileType(mimeType, resourceName) {
    const extension =
        String(resourceName || "")
            .split(".")
            .pop()
            ?.toLowerCase();

    const knownExtensions = {
        zip: "ZIP archive",
        pdf: "PDF document",
        txt: "Text file",
        md: "Markdown file",
        csv: "CSV spreadsheet",
        json: "JSON file",
        doc: "Word document",
        docx: "Word document",
        xls: "Excel spreadsheet",
        xlsx: "Excel spreadsheet",
        png: "PNG image",
        jpg: "JPEG image",
        jpeg: "JPEG image",
        gif: "GIF image",
        mp4: "MP4 video",
        mp3: "MP3 audio",
        py: "Python file",
        java: "Java source file",
        js: "JavaScript file",
        jsx: "React JSX file",
        css: "CSS stylesheet",
    };

    if (knownExtensions[extension]) {
        return knownExtensions[extension];
    }

    if (mimeType === "inode/directory") {
        return "Folder";
    }

    if (mimeType?.startsWith("image/")) {
        return "Image";
    }

    if (mimeType?.startsWith("video/")) {
        return "Video";
    }

    if (mimeType?.startsWith("audio/")) {
        return "Audio";
    }

    return mimeType || null;
}

function extractQuotedName(message) {
    const match = String(message || "").match(/"([^"]+)"/);
    return match?.[1] || null;
}

function getResourceName(notification) {
    return (
        notification.resourceName ||
        extractQuotedName(notification.message) ||
        (
            notification.entityType === "FOLDER"
                ? "Folder"
                : notification.entityType === "FILE"
                    ? "File"
                    : null
        )
    );
}

function titleCase(value) {
    if (!value) {
        return null;
    }

    return String(value)
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/\b\w/g, (letter) =>
            letter.toUpperCase()
        );
}

function compactDetails(details) {
    return details.filter(
        (detail) =>
            detail.value !== null &&
            detail.value !== undefined &&
            detail.value !== ""
    );
}

function getModalDetails(notification) {
    const resourceName = getResourceName(notification);
    const resourceType =
        notification.entityType === "FOLDER"
            ? "Folder"
            : getFriendlyFileType(
                notification.resourceMimeType,
                resourceName
            );

    const size = formatBytes(
        notification.resourceSize
    );

    const permission = titleCase(
        notification.permissionRole
    );

    const actor =
        notification.actorUsername || "Another user";

    switch (notification.type) {
        case "UPLOAD_COMPLETED":
            return compactDetails([
                {
                    label: "Name",
                    value: resourceName,
                },
                {
                    label: "Type",
                    value: resourceType,
                },
                {
                    label: "Size",
                    value: size,
                },
                {
                    label: "Location",
                    value:
                        notification.resourcePath ||
                        "My Drive",
                },
                {
                    label: "Uploaded",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
                {
                    label: "Security scan",
                    value:
                        notification.securityStatus === "CLEAN"
                            ? "No threats found"
                            : "Completed",
                    tone: "success",
                },
            ]);

        case "FILE_SHARED":
        case "FOLDER_SHARED":
        case "PERMISSION_CHANGED":
            return compactDetails([
                {
                    label: "Shared by",
                    value: actor,
                },
                {
                    label: "Item",
                    value: resourceName,
                },
                {
                    label: "Item type",
                    value:
                        notification.entityType === "FOLDER"
                            ? "Folder"
                            : resourceType || "File",
                },
                {
                    label: "Permission",
                    value: permission,
                    tone: "accent",
                },
                {
                    label: "Location",
                    value: notification.resourcePath,
                },
                {
                    label: "Shared",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
            ]);

        case "ACCESS_REVOKED":
            return compactDetails([
                {
                    label: "Removed by",
                    value: actor,
                },
                {
                    label: "Item",
                    value: resourceName,
                },
                {
                    label: "Item type",
                    value:
                        notification.entityType === "FOLDER"
                            ? "Folder"
                            : resourceType || "File",
                },
                {
                    label: "Previous access",
                    value: permission,
                },
                {
                    label: "Removed",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
            ]);

        case "MALWARE_DETECTED":
            return compactDetails([
                {
                    label: "File",
                    value: resourceName,
                },
                {
                    label: "Action taken",
                    value: "Upload blocked and quarantined",
                    tone: "danger",
                },
                {
                    label: "Scan result",
                    value:
                        notification.securityThreat ||
                        "Security threat detected",
                },
                {
                    label: "Detected",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
                {
                    label: "Your drive",
                    value: "Existing files are unaffected",
                    tone: "success",
                },
            ]);

        case "UPLOAD_FAILED":
            return compactDetails([
                {
                    label: "File",
                    value: resourceName,
                },
                {
                    label: "Reason",
                    value:
                        notification.failureReason ||
                        notification.message,
                    tone: "danger",
                },
                {
                    label: "Attempted",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
            ]);

        default:
            return compactDetails([
                {
                    label: "Related item",
                    value: resourceName,
                },
                {
                    label: "Item type",
                    value:
                        notification.entityType === "FOLDER"
                            ? "Folder"
                            : resourceType,
                },
                {
                    label: "Received",
                    value: formatFullDate(
                        notification.createdAt
                    ),
                },
            ]);
    }
}

function getNotificationAction(notification) {
    if (
        notification.type === "FILE_SHARED" ||
        notification.type === "FOLDER_SHARED" ||
        notification.type === "PERMISSION_CHANGED"
    ) {
        return {
            label: "View shared items",
            path: "/shared",
        };
    }

    if (
        notification.type === "UPLOAD_COMPLETED" ||
        notification.type === "ITEM_RESTORED"
    ) {
        return {
            label: "Show in My Drive",
            path: "/main",
        };
    }

    return null;
}

function NotificationDetailsModal({
                                      notification,
                                      onClose,
                                      onNavigate,
                                  }) {
    const closeButtonRef = useRef(null);
    const action = getNotificationAction(notification);
    const typeClass = getTypeClass(notification.type);
    const details = getModalDetails(notification);

    useEffect(() => {
        const previousOverflow =
            document.body.style.overflow;

        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        function handleKeyDown(event) {
            if (event.key === "Escape") {
                onClose();
            }
        }

        document.addEventListener(
            "keydown",
            handleKeyDown
        );

        return () => {
            document.body.style.overflow =
                previousOverflow;

            document.removeEventListener(
                "keydown",
                handleKeyDown
            );
        };
    }, [onClose]);

    return createPortal(
        <div
            className="notification-modal-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section
                className={`notification-modal ${typeClass}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby="notification-modal-title"
                aria-describedby="notification-modal-message"
            >
                <div className="notification-modal-glow" />

                <button
                    ref={closeButtonRef}
                    type="button"
                    className="notification-modal-close"
                    aria-label="Close notification"
                    onClick={onClose}
                >
                    <CloseIcon className="notification-modal-close-icon" />
                </button>

                <header className="notification-modal-hero">
                    <div
                        className={`notification-modal-icon ${typeClass}`}
                    >
                        <NotificationTypeIcon
                            type={notification.type}
                            className="notification-modal-type-svg"
                        />
                    </div>

                    <div className="notification-modal-heading">
                        <span className="notification-modal-kicker">
                            {getTypeLabel(notification.type)}
                        </span>

                        <h2 id="notification-modal-title">
                            {notification.title}
                        </h2>

                        <p className="notification-modal-time">
                            {formatRelativeTime(
                                notification.createdAt
                            )}
                        </p>
                    </div>
                </header>

                <div className="notification-modal-body">
                    <div
                        id="notification-modal-message"
                        className="notification-modal-message-card"
                    >
                        <span>What happened</span>
                        <p>{notification.message}</p>
                    </div>

                    {details.length > 0 && (
                        <section className="notification-modal-context">
                            <h3>
                                {notification.type ===
                                "UPLOAD_COMPLETED"
                                    ? "File details"
                                    : notification.type ===
                                    "MALWARE_DETECTED"
                                        ? "Security details"
                                        : notification.type ===
                                        "ACCESS_REVOKED"
                                            ? "Access details"
                                            : "Details"}
                            </h3>

                            <dl className="notification-modal-details-list">
                                {details.map((detail) => (
                                    <div
                                        key={detail.label}
                                        className={
                                            detail.tone
                                                ? `tone-${detail.tone}`
                                                : ""
                                        }
                                    >
                                        <dt>{detail.label}</dt>
                                        <dd>{detail.value}</dd>
                                    </div>
                                ))}
                            </dl>
                        </section>
                    )}
                </div>

                <footer className="notification-modal-footer">
                    <button
                        type="button"
                        className="notification-modal-secondary"
                        onClick={onClose}
                    >
                        Close
                    </button>

                    {action && (
                        <button
                            type="button"
                            className="notification-modal-primary"
                            onClick={() =>
                                onNavigate(action.path)
                            }
                        >
                            <span>{action.label}</span>
                            <ArrowRightIcon className="notification-modal-action-icon" />
                        </button>
                    )}
                </footer>
            </section>
        </div>,
        document.body
    );
}

function ActivityDetailsModal({
                                  activity,
                                  onClose,
                              }) {
    const closeButtonRef = useRef(null);
    const activityAction = getActivityAction(activity);
    const typeClass =
        getActivityTypeClass(activityAction);
    const details =
        getActivityModalDetails(activity);

    useEffect(() => {
        const previousOverflow =
            document.body.style.overflow;

        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        function handleKeyDown(event) {
            if (event.key === "Escape") {
                onClose();
            }
        }

        document.addEventListener(
            "keydown",
            handleKeyDown
        );

        return () => {
            document.body.style.overflow =
                previousOverflow;

            document.removeEventListener(
                "keydown",
                handleKeyDown
            );
        };
    }, [onClose]);

    return createPortal(
        <div
            className="notification-modal-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section
                className={`notification-modal activity-details-modal ${typeClass}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby="activity-modal-title"
                aria-describedby="activity-modal-summary"
            >
                <div className="notification-modal-glow" />

                <button
                    ref={closeButtonRef}
                    type="button"
                    className="notification-modal-close"
                    aria-label="Close activity details"
                    onClick={onClose}
                >
                    <CloseIcon className="notification-modal-close-icon" />
                </button>

                <header className="notification-modal-hero">
                    <div
                        className={`notification-modal-icon ${typeClass}`}
                    >
                        <ActivityTypeIcon
                            type={activityAction}
                            className="notification-modal-type-svg"
                        />
                    </div>

                    <div className="notification-modal-heading">
                        <span className="notification-modal-kicker">
                            Recent activity
                        </span>

                        <h2 id="activity-modal-title">
                            {activity.title ||
                                formatActivityType(
                                    activity.type
                                )}
                        </h2>

                        <p className="notification-modal-time">
                            {formatRelativeTime(
                                activity.createdAt
                            )}
                        </p>
                    </div>
                </header>

                <div className="notification-modal-body">
                    <div
                        id="activity-modal-summary"
                        className="notification-modal-message-card"
                    >
                        <span>What happened</span>
                        <p>
                            {activity.summary ||
                                activity.title ||
                                formatActivityType(
                                    activity.type
                                )}
                        </p>
                    </div>

                    {details.length > 0 && (
                        <section className="notification-modal-context">
                            <h3>Activity details</h3>

                            <dl className="notification-modal-details-list">
                                {details.map((detail) => (
                                    <div key={detail.label}>
                                        <dt>{detail.label}</dt>
                                        <dd>
                                            {formatActivityDetailValue(
                                                detail
                                            )}
                                        </dd>
                                    </div>
                                ))}
                            </dl>
                        </section>
                    )}
                </div>

                <footer className="notification-modal-footer">
                    <button
                        type="button"
                        className="notification-modal-secondary"
                        onClick={onClose}
                    >
                        Close
                    </button>
                </footer>
            </section>
        </div>,
        document.body
    );
}

export default function NotificationMenu({ onLogout }) {
    const navigate = useNavigate();
    const menuId = useId();
    const menuRef = useRef(null);

    const [open, setOpen] = useState(false);
    const [activeTab, setActiveTab] =
        useState("notifications");
    const [notifications, setNotifications] = useState([]);
    const [activities, setActivities] = useState([]);
    const [activityTotal, setActivityTotal] = useState(0);
    const [activityLoading, setActivityLoading] = useState(false);
    const [activityError, setActivityError] = useState("");
    const [selectedNotification, setSelectedNotification] =
        useState(null);
    const [selectedActivity, setSelectedActivity] =
        useState(null);
    const [activityHistoryOpen, setActivityHistoryOpen] =
        useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const [markingAll, setMarkingAll] = useState(false);
    const [pendingIds, setPendingIds] = useState(
        () => new Set()
    );
    const [error, setError] = useState("");

    const handleRequestError = useCallback(
        (requestError, fallbackMessage) => {
            if (requestError?.name === "AuthError") {
                onLogout?.();
                return;
            }

            setError(
                requestError?.message ||
                fallbackMessage
            );
        },
        [onLogout]
    );

    const refreshUnreadCount = useCallback(async () => {
        try {
            setUnreadCount(
                await getUnreadNotificationCount()
            );
        } catch (requestError) {
            handleRequestError(
                requestError,
                "Failed to load notification count"
            );
        }
    }, [handleRequestError]);

    const loadNotifications = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const response = await getNotifications({
                page: 0,
                size: 10,
            });

            setNotifications(
                Array.isArray(response?.notifications)
                    ? response.notifications
                    : []
            );
        } catch (requestError) {
            handleRequestError(
                requestError,
                "Failed to load notifications"
            );
        } finally {
            setLoading(false);
        }
    }, [handleRequestError]);

    const loadActivities = useCallback(async () => {
        try {
            setActivityLoading(true);
            setActivityError("");

            const response = await getRecentActivity({
                page: 0,
                size: 20,
            });

            setActivities(
                Array.isArray(response?.activities)
                    ? response.activities
                    : []
            );

            setActivityTotal(
                Number(response?.totalElements || 0)
            );
        } catch (requestError) {
            if (requestError?.name === "AuthError") {
                onLogout?.();
                return;
            }

            setActivityError(
                requestError?.message ||
                "Failed to load recent activity"
            );
        } finally {
            setActivityLoading(false);
        }
    }, [onLogout]);

    useEffect(() => {
        /*
         * Load the badge immediately, retry once after authentication has
         * settled, then keep it current while the page remains open.
         */
        const updateUnreadCount = () => {
            void refreshUnreadCount();
        };

        updateUnreadCount();

        const retryId = window.setTimeout(
            updateUnreadCount,
            750
        );

        const intervalId = window.setInterval(
            updateUnreadCount,
            5_000
        );

        function handleWindowFocus() {
            updateUnreadCount();
        }

        function handleVisibilityChange() {
            if (document.visibilityState === "visible") {
                updateUnreadCount();
            }
        }

        window.addEventListener(
            "focus",
            handleWindowFocus
        );

        document.addEventListener(
            "visibilitychange",
            handleVisibilityChange
        );

        return () => {
            window.clearTimeout(retryId);
            window.clearInterval(intervalId);

            window.removeEventListener(
                "focus",
                handleWindowFocus
            );

            document.removeEventListener(
                "visibilitychange",
                handleVisibilityChange
            );
        };
    }, [refreshUnreadCount]);

    useEffect(() => {
        function handleNotificationsChanged() {
            /*
             * A notification was just created by an action in this page.
             * Refresh immediately instead of waiting for the polling timer
             * or for the user to open the bell.
             */
            void refreshUnreadCount();

            if (open) {
                void loadNotifications();
            }
        }

        window.addEventListener(
            NOTIFICATIONS_CHANGED_EVENT,
            handleNotificationsChanged
        );

        return () => {
            window.removeEventListener(
                NOTIFICATIONS_CHANGED_EVENT,
                handleNotificationsChanged
            );
        };
    }, [
        loadNotifications,
        open,
        refreshUnreadCount,
    ]);

    useEffect(() => {
        if (
            !open ||
            activeTab !== "activity"
        ) {
            return undefined;
        }

        void loadActivities();

        const intervalId = window.setInterval(
            loadActivities,
            10_000
        );

        return () => {
            window.clearInterval(intervalId);
        };
    }, [
        activeTab,
        loadActivities,
        open,
    ]);

    useEffect(() => {
        if (!open) {
            return undefined;
        }

        loadNotifications();
        refreshUnreadCount();

        function handleOutsidePointerDown(event) {
            if (!menuRef.current?.contains(event.target)) {
                setOpen(false);
            }
        }

        function handleKeyDown(event) {
            if (event.key === "Escape") {
                setOpen(false);
            }
        }

        document.addEventListener(
            "pointerdown",
            handleOutsidePointerDown
        );
        document.addEventListener(
            "keydown",
            handleKeyDown
        );

        return () => {
            document.removeEventListener(
                "pointerdown",
                handleOutsidePointerDown
            );
            document.removeEventListener(
                "keydown",
                handleKeyDown
            );
        };
    }, [
        loadNotifications,
        open,
        refreshUnreadCount,
    ]);

    async function markReadIfNeeded(notification) {
        if (
            notification.read ||
            pendingIds.has(notification.id)
        ) {
            return notification;
        }

        setPendingIds((current) => {
            const next = new Set(current);
            next.add(notification.id);
            return next;
        });

        try {
            const updated =
                await markNotificationRead(
                    notification.id
                );

            const nextNotification = {
                ...notification,
                ...updated,
                read: true,
            };

            setNotifications((current) =>
                current.map((item) =>
                    item.id === notification.id
                        ? nextNotification
                        : item
                )
            );

            setSelectedNotification((current) =>
                current?.id === notification.id
                    ? nextNotification
                    : current
            );

            setUnreadCount((current) =>
                Math.max(0, current - 1)
            );

            return nextNotification;
        } catch (requestError) {
            handleRequestError(
                requestError,
                "Failed to mark notification as read"
            );

            return notification;
        } finally {
            setPendingIds((current) => {
                const next = new Set(current);
                next.delete(notification.id);
                return next;
            });
        }
    }

    function openNotification(notification) {
        setSelectedNotification(notification);
        setOpen(false);

        if (!notification.read) {
            void markReadIfNeeded(notification);
        }
    }

    async function handleMarkAllRead() {
        if (markingAll || unreadCount === 0) {
            return;
        }

        setMarkingAll(true);
        setError("");

        try {
            await markAllNotificationsRead();

            const readAt = new Date().toISOString();

            setNotifications((current) =>
                current.map((notification) => ({
                    ...notification,
                    read: true,
                    readAt:
                        notification.readAt ||
                        readAt,
                }))
            );

            setUnreadCount(0);
        } catch (requestError) {
            handleRequestError(
                requestError,
                "Failed to mark all notifications as read"
            );
        } finally {
            setMarkingAll(false);
        }
    }

    function handleModalNavigate(path) {
        setSelectedNotification(null);
        navigate(path);
    }

    const badgeText =
        unreadCount > 99
            ? "99+"
            : String(unreadCount);

    return (
        <>
            <div
                className="notification-menu-root"
                ref={menuRef}
            >
                <button
                    type="button"
                    className="notification-menu-trigger"
                    aria-label={
                        unreadCount > 0
                            ? `Notifications, ${unreadCount} unread`
                            : "Notifications"
                    }
                    aria-haspopup="menu"
                    aria-expanded={open}
                    aria-controls={menuId}
                    onClick={() =>
                        setOpen((current) => !current)
                    }
                >
                    <BellIcon className="notification-bell-icon" />

                    {unreadCount > 0 && (
                        <span
                            className="notification-unread-badge"
                            aria-hidden="true"
                        >
                            {badgeText}
                        </span>
                    )}
                </button>

                {open && (
                    <section
                        id={menuId}
                        className="notification-menu-dropdown"
                        aria-label="Notifications and recent activity"
                    >
                        <header className="notification-menu-header">
                            <div>
                                <strong>
                                    {activeTab === "notifications"
                                        ? "Notifications"
                                        : "Recent activity"}
                                </strong>
                                <span>
                                    {activeTab === "notifications"
                                        ? unreadCount > 0
                                            ? `${unreadCount} unread`
                                            : "You're all caught up"
                                        : activityTotal > 0
                                            ? `${activityTotal} recorded actions`
                                            : "Routine actions from your drive"}
                                </span>
                            </div>

                            {activeTab === "notifications" ? (
                                <button
                                    type="button"
                                    className="notification-mark-all"
                                    onClick={handleMarkAllRead}
                                    disabled={
                                        markingAll ||
                                        unreadCount === 0
                                    }
                                >
                                    {markingAll
                                        ? "Marking..."
                                        : "Mark all read"}
                                </button>
                            ) : (
                                <button
                                    type="button"
                                    className="notification-mark-all"
                                    onClick={() => void loadActivities()}
                                    disabled={activityLoading}
                                >
                                    {activityLoading
                                        ? "Refreshing..."
                                        : "Refresh"}
                                </button>
                            )}
                        </header>

                        <div
                            className="notification-menu-tabs"
                            role="tablist"
                            aria-label="Notification views"
                        >
                            <button
                                type="button"
                                role="tab"
                                aria-selected={
                                    activeTab === "notifications"
                                }
                                className={
                                    "notification-menu-tab" +
                                    (
                                        activeTab === "notifications"
                                            ? " is-active"
                                            : ""
                                    )
                                }
                                onClick={() =>
                                    setActiveTab("notifications")
                                }
                            >
                                <BellIcon className="notification-menu-tab-icon" />
                                <span>Notifications</span>

                                {unreadCount > 0 && (
                                    <span className="notification-tab-count">
                                        {badgeText}
                                    </span>
                                )}
                            </button>

                            <button
                                type="button"
                                role="tab"
                                aria-selected={
                                    activeTab === "activity"
                                }
                                className={
                                    "notification-menu-tab" +
                                    (
                                        activeTab === "activity"
                                            ? " is-active"
                                            : ""
                                    )
                                }
                                onClick={() =>
                                    setActiveTab("activity")
                                }
                            >
                                <ActivityIcon className="notification-menu-tab-icon" />
                                <span>Activity</span>
                            </button>
                        </div>

                        {activeTab === "notifications" ? (
                            <div
                                role="tabpanel"
                                className="notification-menu-panel"
                            >
                                {error && (
                                    <div
                                        className="notification-menu-error"
                                        role="alert"
                                    >
                                        {error}
                                    </div>
                                )}

                                <div className="notification-menu-list">
                                    {loading && (
                                        <div className="notification-menu-state">
                                            Loading notifications...
                                        </div>
                                    )}

                                    {!loading &&
                                        notifications.length === 0 && (
                                            <div className="notification-menu-state">
                                                No notifications yet.
                                            </div>
                                        )}

                                    {!loading &&
                                        notifications.map(
                                            (notification) => (
                                                <button
                                                    key={notification.id}
                                                    type="button"
                                                    className={
                                                        "notification-menu-item" +
                                                        (
                                                            notification.read
                                                                ? ""
                                                                : " is-unread"
                                                        )
                                                    }
                                                    disabled={pendingIds.has(
                                                        notification.id
                                                    )}
                                                    onClick={() =>
                                                        openNotification(
                                                            notification
                                                        )
                                                    }
                                                >
                                                    <span
                                                        className={
                                                            "notification-type-icon " +
                                                            getTypeClass(
                                                                notification.type
                                                            )
                                                        }
                                                    >
                                                        <NotificationTypeIcon
                                                            type={
                                                                notification.type
                                                            }
                                                        />
                                                    </span>

                                                    <span className="notification-menu-copy">
                                                        <span className="notification-menu-title-row">
                                                            <strong>
                                                                {
                                                                    notification.title
                                                                }
                                                            </strong>

                                                            {!notification.read && (
                                                                <span
                                                                    className="notification-unread-dot"
                                                                    aria-label="Unread"
                                                                />
                                                            )}
                                                        </span>

                                                        <span className="notification-menu-message">
                                                            {
                                                                notification.message
                                                            }
                                                        </span>

                                                        <time
                                                            dateTime={
                                                                notification.createdAt
                                                            }
                                                        >
                                                            {formatRelativeTime(
                                                                notification.createdAt
                                                            )}
                                                        </time>
                                                    </span>
                                                </button>
                                            )
                                        )}
                                </div>
                            </div>
                        ) : (
                            <div
                                role="tabpanel"
                                className="notification-menu-panel notification-activity-panel"
                            >
                                {activityError && (
                                    <div
                                        className="notification-menu-error"
                                        role="alert"
                                    >
                                        {activityError}
                                    </div>
                                )}

                                {activityLoading &&
                                    activities.length === 0 && (
                                        <div className="notification-menu-state">
                                            Loading recent activity...
                                        </div>
                                    )}

                                {!activityLoading &&
                                    !activityError &&
                                    activities.length === 0 && (
                                        <div className="notification-activity-empty">
                                            <span className="notification-activity-empty-icon">
                                                <ActivityIcon className="notification-activity-empty-svg" />
                                            </span>

                                            <strong>No recent activity</strong>

                                            <p>
                                                Actions recorded in your
                                                activity log will appear here.
                                            </p>
                                        </div>
                                    )}

                                {activities.length > 0 && (
                                    <div className="notification-activity-list">
                                        {activities.map(
                                            (activity, index) => (
                                                <button
                                                    key={
                                                        activity.id ??
                                                        `${activity.type}-${activity.createdAt}-${index}`
                                                    }
                                                    type="button"
                                                    className={
                                                        "notification-activity-item " +
                                                        getActivityTypeClass(
                                                            activity.type
                                                        )
                                                    }
                                                    onClick={() => {
                                                        setSelectedActivity(
                                                            activity
                                                        );
                                                        setOpen(false);
                                                    }}
                                                >
                                                    <span className="notification-activity-item-icon">
                                                        <ActivityTypeIcon
                                                            type={
                                                                getActivityAction(activity)
                                                            }
                                                        />
                                                    </span>

                                                    <span className="notification-activity-copy">
                                                        <span className="notification-activity-title-row">
                                                            <strong>
                                                                {activity.title ||
                                                                    formatActivityType(
                                                                        activity.type
                                                                    )}
                                                            </strong>

                                                            {activity.entityType && (
                                                                <span className="notification-activity-entity">
                                                                    {formatActivityType(
                                                                        activity.entityType
                                                                    )}
                                                                </span>
                                                            )}
                                                        </span>

                                                        {activity.resourceName && (
                                                            <span className="notification-activity-resource-name">
                                                                {
                                                                    activity.resourceName
                                                                }
                                                            </span>
                                                        )}

                                                        <span className="notification-activity-summary">
                                                            {activity.summary ||
                                                                activity.title ||
                                                                formatActivityType(
                                                                    activity.type
                                                                )}
                                                        </span>

                                                        <time
                                                            dateTime={
                                                                activity.createdAt
                                                            }
                                                            title={formatFullDate(
                                                                activity.createdAt
                                                            )}
                                                        >
                                                            {formatRelativeTime(
                                                                activity.createdAt
                                                            )}
                                                        </time>
                                                    </span>
                                                </button>
                                            )
                                        )}
                                    </div>
                                )}

                                {activities.length > 0 && (
                                    <div className="notification-activity-footer">
                                        <button
                                            type="button"
                                            className="notification-activity-show-all"
                                            onClick={() => {
                                                setOpen(false);
                                                setActivityHistoryOpen(true);
                                            }}
                                        >
                                            <span>Show all activity</span>
                                            <ArrowRightIcon className="notification-activity-show-all-icon" />
                                        </button>
                                    </div>
                                )}
                            </div>
                        )}
                    </section>
                )}
            </div>

            {selectedNotification && (
                <NotificationDetailsModal
                    notification={selectedNotification}
                    onClose={() =>
                        setSelectedNotification(null)
                    }
                    onNavigate={handleModalNavigate}
                />
            )}

            {selectedActivity && (
                <ActivityDetailsModal
                    activity={selectedActivity}
                    onClose={() =>
                        setSelectedActivity(null)
                    }
                />
            )}

            {activityHistoryOpen && (
                <ActivityHistoryModal
                    onClose={() =>
                        setActivityHistoryOpen(false)
                    }
                    onSelectActivity={(activity) => {
                        setActivityHistoryOpen(false);
                        setSelectedActivity(activity);
                    }}
                    onAuthError={onLogout}
                />
            )}
        </>
    );
}
