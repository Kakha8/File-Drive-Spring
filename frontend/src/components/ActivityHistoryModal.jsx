import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { createPortal } from "react-dom";
import {
    getActivityTypes,
    getRecentActivity,
} from "../api/activity";

const PAGE_SIZE = 20;

function Icon({
                  children,
                  className = "",
              }) {
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

function CloseIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="m6 6 12 12" />
            <path d="m18 6-12 12" />
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

function UploadIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M12 16V4" />
            <path d="m7 9 5-5 5 5" />
            <path d="M4 20h16" />
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

function RestoreIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M3 7v5h5" />
            <path d="M5.7 17.3a8 8 0 1 0-.7-9.6L3 12" />
            <path d="M12 8v4l2.5 1.5" />
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

function StarIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M12 3.5 14.7 9l6 .9-4.35 4.25 1.05 6L12 17.3l-5.4 2.85 1.05-6L3.3 9.9l6-.9z" />
        </Icon>
    );
}

function ChevronLeftIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="m15 18-6-6 6-6" />
        </Icon>
    );
}

function ChevronRightIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="m9 18 6-6-6-6" />
        </Icon>
    );
}

function FilterIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M4 5h16" />
            <path d="M7 12h10" />
            <path d="M10 19h4" />
        </Icon>
    );
}

function ActivityTypeIcon({
                              type,
                              className = "activity-history-item-svg",
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

    if (value.includes("FAVORITE")) {
        return <StarIcon className={className} />;
    }

    return <ActivityIcon className={className} />;
}

function formatType(type) {
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

function getTypeClass(type) {
    return `activity-${String(type || "activity")
        .toLowerCase()
        .replaceAll("_", "-")}`;
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

function parseLocalDate(dateValue) {
    if (!dateValue) {
        return null;
    }

    const [year, month, day] =
        dateValue
            .split("-")
            .map(Number);

    if (
        !year ||
        !month ||
        !day
    ) {
        return null;
    }

    return new Date(
        year,
        month - 1,
        day,
        0,
        0,
        0,
        0
    );
}

function startOfLocalDayIso(dateValue) {
    return parseLocalDate(dateValue)
        ?.toISOString() || null;
}

function nextLocalDayIso(dateValue) {
    const date = parseLocalDate(dateValue);

    if (!date) {
        return null;
    }

    date.setDate(date.getDate() + 1);

    return date.toISOString();
}

function buildDateFilter({
                             mode,
                             singleDate,
                             fromDate,
                             toDate,
                         }) {
    if (mode === "single") {
        if (!singleDate) {
            throw new Error("Choose a date.");
        }

        return {
            from: startOfLocalDayIso(singleDate),
            to: nextLocalDayIso(singleDate),
        };
    }

    if (mode === "range") {
        if (!fromDate || !toDate) {
            throw new Error(
                "Choose both the start and end dates."
            );
        }

        if (fromDate > toDate) {
            throw new Error(
                "The start date must be before the end date."
            );
        }

        return {
            from: startOfLocalDayIso(fromDate),
            to: nextLocalDayIso(toDate),
        };
    }

    return {
        from: null,
        to: null,
    };
}

function getVisiblePages(
    currentPage,
    totalPages
) {
    if (totalPages <= 1) {
        return [0];
    }

    const pages = new Set([
        0,
        totalPages - 1,
        currentPage - 1,
        currentPage,
        currentPage + 1,
    ]);

    return [...pages]
        .filter(
            (page) =>
                page >= 0 &&
                page < totalPages
        )
        .sort((a, b) => a - b);
}

export default function ActivityHistoryModal({
                                                 onClose,
                                                 onSelectActivity,
                                                 onAuthError,
                                             }) {
    const closeButtonRef = useRef(null);

    const [activities, setActivities] = useState([]);
    const [activityTypes, setActivityTypes] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [typesLoading, setTypesLoading] = useState(true);
    const [error, setError] = useState("");

    const [dateMode, setDateMode] = useState("all");
    const [singleDate, setSingleDate] = useState("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [selectedTypes, setSelectedTypes] =
        useState(() => new Set());

    const [appliedFilters, setAppliedFilters] =
        useState({
            from: null,
            to: null,
            types: [],
        });

    const visiblePages = useMemo(
        () => getVisiblePages(page, totalPages),
        [page, totalPages]
    );

    const loadPage = useCallback(async () => {
        try {
            setLoading(true);
            setError("");

            const response = await getRecentActivity({
                page,
                size: PAGE_SIZE,
                from: appliedFilters.from,
                to: appliedFilters.to,
                types: appliedFilters.types,
            });

            setActivities(
                Array.isArray(response?.activities)
                    ? response.activities
                    : []
            );

            setTotalElements(
                Number(response?.totalElements || 0)
            );

            setTotalPages(
                Number(response?.totalPages || 0)
            );
        } catch (requestError) {
            if (requestError?.name === "AuthError") {
                onAuthError?.();
                return;
            }

            setError(
                requestError?.message ||
                "Failed to load activity history"
            );
        } finally {
            setLoading(false);
        }
    }, [
        appliedFilters,
        onAuthError,
        page,
    ]);

    useEffect(() => {
        void loadPage();
    }, [loadPage]);

    useEffect(() => {
        let active = true;

        async function loadTypes() {
            try {
                setTypesLoading(true);

                const types =
                    await getActivityTypes();

                if (active) {
                    setActivityTypes(types);
                }
            } catch (requestError) {
                if (requestError?.name === "AuthError") {
                    onAuthError?.();
                    return;
                }

                if (active) {
                    setError(
                        requestError?.message ||
                        "Failed to load activity types"
                    );
                }
            } finally {
                if (active) {
                    setTypesLoading(false);
                }
            }
        }

        void loadTypes();

        return () => {
            active = false;
        };
    }, [onAuthError]);

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

    function toggleType(type) {
        setSelectedTypes((current) => {
            const next = new Set(current);

            if (next.has(type)) {
                next.delete(type);
            } else {
                next.add(type);
            }

            return next;
        });
    }

    function applyFilters(event) {
        event.preventDefault();

        try {
            const dateFilter =
                buildDateFilter({
                    mode: dateMode,
                    singleDate,
                    fromDate,
                    toDate,
                });

            setError("");
            setPage(0);
            setAppliedFilters({
                ...dateFilter,
                types: [...selectedTypes],
            });
        } catch (filterError) {
            setError(filterError.message);
        }
    }

    function clearFilters() {
        setDateMode("all");
        setSingleDate("");
        setFromDate("");
        setToDate("");
        setSelectedTypes(new Set());
        setError("");
        setPage(0);
        setAppliedFilters({
            from: null,
            to: null,
            types: [],
        });
    }

    return createPortal(
        <div
            className="activity-history-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section
                className="activity-history-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="activity-history-title"
            >
                <header className="activity-history-header">
                    <div>
                        <span className="activity-history-kicker">
                            Drive history
                        </span>

                        <h2 id="activity-history-title">
                            All activity
                        </h2>

                        <p>
                            {totalElements === 1
                                ? "1 matching action"
                                : `${totalElements} matching actions`}
                        </p>
                    </div>

                    <button
                        ref={closeButtonRef}
                        type="button"
                        className="activity-history-close"
                        aria-label="Close activity history"
                        onClick={onClose}
                    >
                        <CloseIcon className="activity-history-close-icon" />
                    </button>
                </header>

                <div className="activity-history-layout">
                    <aside className="activity-history-filters">
                        <div className="activity-history-filter-heading">
                            <FilterIcon className="activity-history-filter-icon" />
                            <strong>Filters</strong>
                        </div>

                        <form onSubmit={applyFilters}>
                            <label className="activity-history-field">
                                <span>Date filter</span>

                                <select
                                    value={dateMode}
                                    onChange={(event) =>
                                        setDateMode(
                                            event.target.value
                                        )
                                    }
                                >
                                    <option value="all">
                                        All time
                                    </option>
                                    <option value="single">
                                        Single date
                                    </option>
                                    <option value="range">
                                        Date range
                                    </option>
                                </select>
                            </label>

                            {dateMode === "single" && (
                                <label className="activity-history-field">
                                    <span>Date</span>
                                    <input
                                        type="date"
                                        value={singleDate}
                                        onChange={(event) =>
                                            setSingleDate(
                                                event.target.value
                                            )
                                        }
                                    />
                                </label>
                            )}

                            {dateMode === "range" && (
                                <div className="activity-history-date-range">
                                    <label className="activity-history-field">
                                        <span>From</span>
                                        <input
                                            type="date"
                                            value={fromDate}
                                            max={toDate || undefined}
                                            onChange={(event) =>
                                                setFromDate(
                                                    event.target.value
                                                )
                                            }
                                        />
                                    </label>

                                    <label className="activity-history-field">
                                        <span>To</span>
                                        <input
                                            type="date"
                                            value={toDate}
                                            min={fromDate || undefined}
                                            onChange={(event) =>
                                                setToDate(
                                                    event.target.value
                                                )
                                            }
                                        />
                                    </label>
                                </div>
                            )}

                            <fieldset className="activity-history-types">
                                <legend>Activity types</legend>

                                {typesLoading ? (
                                    <p className="activity-history-types-state">
                                        Loading types...
                                    </p>
                                ) : activityTypes.length === 0 ? (
                                    <p className="activity-history-types-state">
                                        No activity types available.
                                    </p>
                                ) : (
                                    <div className="activity-history-type-list">
                                        {activityTypes.map((type) => (
                                            <label
                                                key={type.value}
                                                className="activity-history-type-option"
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={selectedTypes.has(
                                                        type.value
                                                    )}
                                                    onChange={() =>
                                                        toggleType(
                                                            type.value
                                                        )
                                                    }
                                                />
                                                <span>
                                                    {type.label ||
                                                        formatType(
                                                            type.value
                                                        )}
                                                </span>
                                            </label>
                                        ))}
                                    </div>
                                )}
                            </fieldset>

                            <div className="activity-history-filter-actions">
                                <button
                                    type="submit"
                                    className="activity-history-apply"
                                >
                                    Apply filters
                                </button>

                                <button
                                    type="button"
                                    className="activity-history-clear"
                                    onClick={clearFilters}
                                >
                                    Clear
                                </button>
                            </div>
                        </form>
                    </aside>

                    <main className="activity-history-content">
                        {error && (
                            <div
                                className="activity-history-error"
                                role="alert"
                            >
                                {error}
                            </div>
                        )}

                        <div className="activity-history-list">
                            {loading && (
                                <div className="activity-history-state">
                                    Loading activity...
                                </div>
                            )}

                            {!loading &&
                                activities.length === 0 && (
                                    <div className="activity-history-empty">
                                        <ActivityIcon className="activity-history-empty-icon" />
                                        <strong>No matching activity</strong>
                                        <p>
                                            Adjust the date or activity type
                                            filters and try again.
                                        </p>
                                    </div>
                                )}

                            {!loading &&
                                activities.map((activity) => (
                                    <button
                                        key={activity.id}
                                        type="button"
                                        className={
                                            "activity-history-item " +
                                            getTypeClass(
                                                activity.type
                                            )
                                        }
                                        onClick={() =>
                                            onSelectActivity?.(
                                                activity
                                            )
                                        }
                                    >
                                        <span className="activity-history-item-icon">
                                            <ActivityTypeIcon
                                                type={activity.type}
                                            />
                                        </span>

                                        <span className="activity-history-item-copy">
                                            <span className="activity-history-item-title">
                                                <strong>
                                                    {activity.title ||
                                                        formatType(
                                                            activity.type
                                                        )}
                                                </strong>

                                                {activity.entityType && (
                                                    <span>
                                                        {formatType(
                                                            activity.entityType
                                                        )}
                                                    </span>
                                                )}
                                            </span>

                                            {activity.resourceName && (
                                                <span className="activity-history-resource">
                                                    {activity.resourceName}
                                                </span>
                                            )}

                                            <span className="activity-history-summary">
                                                {activity.summary ||
                                                    activity.title ||
                                                    formatType(
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
                                ))}
                        </div>

                        <footer className="activity-history-pagination">
                            <span>
                                {totalElements === 0
                                    ? "No results"
                                    : `Page ${page + 1} of ${Math.max(
                                        totalPages,
                                        1
                                    )}`}
                            </span>

                            <div>
                                <button
                                    type="button"
                                    aria-label="Previous page"
                                    disabled={
                                        loading ||
                                        page === 0
                                    }
                                    onClick={() =>
                                        setPage((current) =>
                                            Math.max(
                                                0,
                                                current - 1
                                            )
                                        )
                                    }
                                >
                                    <ChevronLeftIcon className="activity-history-page-icon" />
                                </button>

                                {visiblePages.map(
                                    (pageNumber, index) => {
                                        const previous =
                                            visiblePages[index - 1];

                                        return (
                                            <span
                                                key={pageNumber}
                                                className="activity-history-page-group"
                                            >
                                                {previous !== undefined &&
                                                    pageNumber - previous > 1 && (
                                                        <span className="activity-history-page-gap">
                                                            …
                                                        </span>
                                                    )}

                                                <button
                                                    type="button"
                                                    className={
                                                        pageNumber === page
                                                            ? "is-current"
                                                            : ""
                                                    }
                                                    aria-current={
                                                        pageNumber === page
                                                            ? "page"
                                                            : undefined
                                                    }
                                                    disabled={loading}
                                                    onClick={() =>
                                                        setPage(
                                                            pageNumber
                                                        )
                                                    }
                                                >
                                                    {pageNumber + 1}
                                                </button>
                                            </span>
                                        );
                                    }
                                )}

                                <button
                                    type="button"
                                    aria-label="Next page"
                                    disabled={
                                        loading ||
                                        totalPages === 0 ||
                                        page >= totalPages - 1
                                    }
                                    onClick={() =>
                                        setPage((current) =>
                                            Math.min(
                                                totalPages - 1,
                                                current + 1
                                            )
                                        )
                                    }
                                >
                                    <ChevronRightIcon className="activity-history-page-icon" />
                                </button>
                            </div>
                        </footer>
                    </main>
                </div>
            </section>
        </div>,
        document.body
    );
}