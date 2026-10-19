@file:Suppress("MagicNumber")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.ui.graphics.vector.ImageVector

/** The sections the icon picker groups its icons in. */
internal enum class GroupIconCategory { General, Security, Internet, Finance, Devices, Work }

/**
 * One icon a group can wear. [id] is the standard KeePass icon number the vault stores, so the choice survives in the
 * file and other clients show some icon; the drawing is KeyNexa's own.
 */
internal class GroupIcon(
    val id: Int,
    val category: GroupIconCategory,
    private val paths: List<String>,
) {
    val vector: ImageVector by lazy { icon(name = "Group$id", paths = paths) }
}

/** The icon of a group that has not chosen one, and of one whose number is not in the catalogue. */
internal const val DEFAULT_GROUP_ICON = 48

/** The standard icon KeePass gives the recycle bin. */
private const val BIN_ICON = 43

/** A round-capped zero-length line: a dot. */
private const val DOT = "h.01"

private fun circle(
    cx: Int,
    cy: Int,
    r: Int,
): String = "M${cx + r} ${cy}a$r $r 0 1 1-${2 * r} 0 $r $r 0 0 1 ${2 * r} 0z"

private fun card(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
): String =
    "M${x + 1} ${y}h${w - 2}a1 1 0 0 1 1 1v${h - 2}a1 1 0 0 1-1 1H${x + 1}a1 1 0 0 1-1-1V${y + 1}a1 1 0 0 1 1-1z"

private const val FOLDER = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
private const val SHEET = "M6 3h9l4 4v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"

/** About fifty icons in six categories, in the order the picker shows them. */
internal val GroupIconCatalog: List<GroupIcon> =
    listOf(
        GroupIcon(48, GroupIconCategory.General, listOf(FOLDER)),
        GroupIcon(
            49,
            GroupIconCategory.General,
            listOf(
                "M3 18V6a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v2",
                "M3 19l2.6-8a1.5 1.5 0 0 1 1.4-1h14.2a.8.8 0 0 1 .8 1L19.7 18a2 2 0 0 1-1.9 1H3z",
            ),
        ),
        GroupIcon(50, GroupIconCategory.General, listOf(FOLDER, "M12 10v5M9.5 12.5h5")),
        GroupIcon(
            61,
            GroupIconCategory.General,
            listOf("M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z"),
        ),
        GroupIcon(60, GroupIconCategory.General, listOf("M4 11l8-7 8 7", "M6 10v10h12V10", "M10 20v-5h4v5")),
        GroupIcon(
            56,
            GroupIconCategory.General,
            listOf("M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z", "M5 17a3 3 0 0 1 3-3h11"),
        ),
        GroupIcon(
            57,
            GroupIconCategory.General,
            listOf("M9 6h11M9 12h11M9 18h11", "M4.5 6$DOT", "M4.5 12$DOT", "M4.5 18$DOT"),
        ),
        GroupIcon(53, GroupIconCategory.General, listOf(circle(12, 12, 9), "M8 12.5l3 3 5-6")),
        GroupIcon(46, GroupIconCategory.General, listOf(circle(12, 12, 9), "M12 11v5", "M12 8$DOT")),
        GroupIcon(2, GroupIconCategory.General, listOf("M12 4L3 20h18z", "M12 10v4", "M12 17$DOT")),
        GroupIcon(0, GroupIconCategory.Security, listOf(circle(7, 15, 3), "M9.5 12.5L20 3", "M16 7l3 3")),
        GroupIcon(
            13,
            GroupIconCategory.Security,
            listOf(circle(5, 14, 2), "M6.5 12.5L15 4", "M12 7l2 2", circle(10, 19, 2), "M11.5 17.5L20 9", "M17 12l2 2"),
        ),
        GroupIcon(52, GroupIconCategory.Security, listOf(card(5, 11, 14, 10), "M8 11V8a4 4 0 0 1 8 0v3", "M12 15v2")),
        GroupIcon(51, GroupIconCategory.Security, listOf(card(5, 11, 14, 10), "M8 11V8a4 4 0 0 1 7.5-2", "M12 15v2")),
        GroupIcon(
            58,
            GroupIconCategory.Security,
            listOf("M12 3l8 3v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z", "M9 12l2 2 4-4"),
        ),
        GroupIcon(
            67,
            GroupIconCategory.Security,
            listOf(card(4, 4, 16, 11), "M8 8h8M8 11h5", "M14 15l-1 5 2-1 2 1-1-5"),
        ),
        GroupIcon(
            1,
            GroupIconCategory.Internet,
            listOf(
                circle(12, 12, 9),
                "M3 12h18",
                "M12 3c2.6 2.6 4 5.6 4 9s-1.4 6.4-4 9c-2.6-2.6-4-5.6-4-9s1.4-6.4 4-9z",
            ),
        ),
        GroupIcon(16, GroupIconCategory.Internet, listOf("M6 4h12v17l-6-4-6 4z")),
        GroupIcon(
            8,
            GroupIconCategory.Internet,
            listOf(
                "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1",
                "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
            ),
        ),
        GroupIcon(
            35,
            GroupIconCategory.Internet,
            listOf("M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9.5 4.3 4.3 0 0 1 17.5 18z"),
        ),
        GroupIcon(
            65,
            GroupIconCategory.Internet,
            listOf("M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z", circle(12, 10, 2)),
        ),
        GroupIcon(19, GroupIconCategory.Internet, listOf(card(3, 6, 18, 12), "M3 7l9 6 9-6")),
        GroupIcon(
            25,
            GroupIconCategory.Internet,
            listOf("M3 13l3-8h12l3 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z", "M3 13h5l1 3h6l1-3h5"),
        ),
        GroupIcon(
            5,
            GroupIconCategory.Internet,
            listOf("M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H10l-5 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"),
        ),
        GroupIcon(
            37,
            GroupIconCategory.Finance,
            listOf("M3 9l9-5 9 5", "M4 9h16", "M6 11v7M10 11v7M14 11v7M18 11v7", "M3 20h18"),
        ),
        GroupIcon(
            66,
            GroupIconCategory.Finance,
            listOf("M3 7h18v10H3z", circle(12, 12, 2), "M6.5 12$DOT", "M17.5 12$DOT"),
        ),
        GroupIcon(10, GroupIconCategory.Finance, listOf(card(3, 5, 18, 14), "M3 10h18", "M6 15h4")),
        GroupIcon(18, GroupIconCategory.Devices, listOf(card(3, 4, 18, 12), "M9 20h6", "M12 16v4")),
        GroupIcon(23, GroupIconCategory.Devices, listOf(card(7, 3, 10, 18), "M11 18h2")),
        GroupIcon(
            3,
            GroupIconCategory.Devices,
            listOf(card(4, 4, 16, 7), card(4, 13, 16, 7), "M8 7.5$DOT", "M8 16.5$DOT"),
        ),
        GroupIcon(
            26,
            GroupIconCategory.Devices,
            listOf("M5 4h11l3 3v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M8 4v5h7V4", "M8 20v-6h8v6"),
        ),
        GroupIcon(
            27,
            GroupIconCategory.Devices,
            listOf("M3 14l3-8h12l3 8v4a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z", "M3 14h18", "M7 17$DOT"),
        ),
        GroupIcon(
            42,
            GroupIconCategory.Devices,
            listOf("M7 7h10v10H7z", "M10 10h4v4h-4z", "M9 3v4M15 3v4M9 17v4M15 17v4M3 9h4M3 15h4M17 9h4M17 15h4"),
        ),
        GroupIcon(17, GroupIconCategory.Devices, listOf(circle(12, 12, 9), circle(12, 12, 3))),
        GroupIcon(
            11,
            GroupIconCategory.Devices,
            listOf(
                "M4 7h3l2-3h6l2 3h3a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z",
                circle(12, 13, 4),
            ),
        ),
        GroupIcon(
            31,
            GroupIconCategory.Devices,
            listOf(
                "M7 9V4h10v5",
                "M7 17H5a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-2",
                "M7 14h10v6H7z",
            ),
        ),
        GroupIcon(7, GroupIconCategory.Work, listOf(SHEET, "M14 3v5h5", "M8 12h8M8 16h8")),
        GroupIcon(
            21,
            GroupIconCategory.Work,
            listOf(
                "M8 4h8v3H8z",
                "M16 5.5h2a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1h2",
                "M9 13l2 2 4-4",
            ),
        ),
        GroupIcon(22, GroupIconCategory.Work, listOf(SHEET, "M12 11v6M9 14h6")),
        GroupIcon(
            34,
            GroupIconCategory.Work,
            listOf("M4 7h10M18 7h2M4 17h2M10 17h10", circle(16, 7, 2), circle(8, 17, 2)),
        ),
        GroupIcon(
            59,
            GroupIconCategory.Work,
            listOf(
                "M15 5a4 4 0 0 0-3.8 5.2L4 17.5 6.5 20l7.3-7.2A4 4 0 0 0 19 9l-2.5 2.5-2.5-.5-.5-2.5L16 6z",
            ),
        ),
        GroupIcon(30, GroupIconCategory.Work, listOf(card(3, 5, 18, 14), "M7 10l3 2-3 2", "M12 15h5")),
        GroupIcon(39, GroupIconCategory.Work, listOf(circle(12, 12, 9), "M12 7v5l3 2")),
        GroupIcon(54, GroupIconCategory.Work, listOf("M4 20l1-4L16.5 4.5a2 2 0 0 1 3 3L8 19z", "M14 7l3 3")),
        GroupIcon(
            9,
            GroupIconCategory.Work,
            listOf(card(3, 5, 18, 14), circle(8, 11, 2), "M5.5 16a3 3 0 0 1 5 0", "M14 10h4M14 14h4"),
        ),
        GroupIcon(
            47,
            GroupIconCategory.Work,
            listOf("M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M4 7.5l8 4.5 8-4.5", "M12 12v9"),
        ),
        GroupIcon(
            36,
            GroupIconCategory.Work,
            listOf(
                "M4 4h16a1 1 0 0 1 1 1v3H3V5a1 1 0 0 1 1-1z",
                "M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8",
                "M10 12h4",
            ),
        ),
        GroupIcon(14, GroupIconCategory.Work, listOf("M13 3L5 13.5h6L10 21l8-10.5h-6z")),
    )

private val GroupIconsById: Map<Int, GroupIcon> = GroupIconCatalog.associateBy { it.id }

/** The drawing for the standard icon [id]: the bin keeps its own, and a number outside the catalogue is a folder. */
internal fun groupIconVector(id: Int): ImageVector =
    if (id == BIN_ICON) TrashIcon else (GroupIconsById[id] ?: GroupIconsById.getValue(DEFAULT_GROUP_ICON)).vector
