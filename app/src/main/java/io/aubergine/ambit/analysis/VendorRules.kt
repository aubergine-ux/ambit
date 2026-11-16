package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.DeviceCategory

/**
 * Vendor-name keyword rules, kept separate from the registry lookup.
 *
 * Split out for two reasons: it is the part most likely to be edited as vendors are added,
 * and it is pure, so it can be tested without standing up an Android Context.
 */
object VendorRules {

    /**
     * Ordered most-specific first; the first matching bucket wins. Plenty of vendors sell
     * into more than one market, and where they do we prefer the more operationally
     * interesting label.
     *
     * This classifies a *manufacturer*, not a device or its owner. A Cradlepoint router in
     * a bucket labelled "Fleet" might be in a food truck.
     */
    fun categoryFor(vendor: String): DeviceCategory {
        val v = vendor.lowercase()
        Tables.ALL.forEach { rule ->
            if (rule.keywords.any { v.contains(it) }) return rule.category
        }
        return DeviceCategory.UNKNOWN
    }

    private data class Rule(val category: DeviceCategory, val keywords: List<String>)

    private object Tables {

        /**
         * Ordered most-specific first. Keywords are matched as substrings of the
         * lowercased vendor name, so keep them long enough not to collide — "axon" is safe,
         * "ring" would match "Ubiquiti Networks Engineering" and is not.
         */
        val ALL = listOf(
            Rule(
                DeviceCategory.PRIORITY,
                listOf(
                    "axon enterprise", "axon", "l3harris", "harris corporation",
                    "motorola solutions", "tait communications", "tait international",
                    "ef johnson", "efjohnson", "bk technologies", "relm wireless",
                    "codan", "digital ally", "watchguard video", "coban technologies",
                    "utility associates", "getac", "whelen engineering", "federal signal",
                    "sierra madre", "safe fleet", "vievu", "wolfcom", "reveal media",
                    "police", "public safety", "law enforcement", "sheriff",
                    "kustom signals", "decatur electronics", "applied concepts",
                    "stalker radar", "zetron", "avtec", "spillman", "priority dispatch",
                    "elbit", "leonardo drs", "flir", "teledyne flir", "axis defense",
                    "cellebrite", "nuix", "magnet forensics", "grayshift",
                ),
            ),
            Rule(
                DeviceCategory.FLEET,
                listOf(
                    "cradlepoint", "sierra wireless", "telit", "digi international",
                    "peplink", "inseego", "novatel wireless", "inhand networks",
                    "robustel", "multi-tech", "multitech", "teltonika", "calamp",
                    "geotab", "samsara", "lantronix", "encore networks", "baicells",
                    "quectel", "fibocom", "u-blox", "ublox", "simcom", "meiglink",
                    "advantech b+b", "moxa", "westermo", "netmodule", "cel-fi",
                    "nextivity", "kajeet", "cambium", "usat corp", "usat ", "milesight",
                    "positioning universal", "danlaw", "queclink", "meitrack",
                    "ruptela", "gosafe", "atrack", "concox", "jimi iot", "howen",
                    "streamax", "rosco vision", "lytx", "smartwitness", "netradyne",
                ),
            ),
            Rule(
                DeviceCategory.INFRASTRUCTURE,
                listOf(
                    "cisco", "meraki", "juniper", "aruba", "hewlett packard", "hpe",
                    "ubiquiti", "ruckus", "extreme networks", "fortinet", "mikrotik",
                    "alcatel-lucent", "alcatel lucent", "nokia", "ericsson", "huawei",
                    "zte corporation", "commscope", "adtran", "calix", "zhone",
                    "brocade", "arista", "f5 networks", "palo alto networks",
                    "watchguard technologies", "sophos", "sonicwall", "check point",
                    "cambium networks", "aerohive", "mist systems", "tarana",
                    "ceragon", "dragonwave", "aviat networks", "proxim", "airspan",
                    "casa systems", "harmonic", "vecima", "teleste", "corning optical",
                    "raycap", "jma wireless", "westell", "amphenol antenna",
                    "kathrein", "rosenberger", "smartsky", "siae microelettronica",
                ),
            ),
            Rule(
                DeviceCategory.COMMERCIAL,
                listOf(
                    "zebra technologies", "honeywell", "datalogic", "verifone",
                    "ingenico", "ncr ", "ncr corporation", "elo touch", "star micronics",
                    "square, inc", "block, inc", "toast", "clover network", "pax computer",
                    "hikvision", "dahua", "hanwha", "vivotek", "axis communications",
                    "avigilon", "genetec", "milestone systems", "mobotix", "pelco",
                    "bosch security", "bosch sicherheitssysteme", "uniview", "tiandy",
                    "lorex", "swann", "amcrest", "reolink", "verkada", "eagle eye",
                    "openpath", "brivo", "hid global", "assa abloy", "allegion",
                    "schlage", "salto systems", "kaba", "dormakaba", "napco",
                    "johnson controls", "tyco", "siemens", "schneider electric",
                    "rockwell automation", "emerson", "yokogawa", "abb ", "ge digital",
                    "crestron", "extron", "biamp", "qsc", "shure", "sennheiser",
                    "barco", "christie digital", "planar systems", "daktronics",
                    "brightsign", "scala", "stratacache", "intermec", "impinj",
                    "alien technology", "sato", "toshiba tec", "epson", "brother",
                ),
            ),
            Rule(
                DeviceCategory.RESIDENTIAL,
                listOf(
                    "arris", "technicolor", "sagemcom", "netgear", "tp-link", "tplink",
                    "d-link", "dlink", "belkin", "linksys", "asustek", "asus",
                    "actiontec", "2wire", "pace ", "zyxel", "hitron", "ubee",
                    "sercomm", "askey", "compal broadband", "humax", "vantiva",
                    "amazon technologies", "amazon.com", "google, inc", "google llc",
                    "nest labs", "ring llc", "wyze labs", "roku", "sonos", "eero",
                    "apple, inc", "apple inc", "samsung electronics", "lg electronics",
                    "vizio", "tcl ", "hisense", "sharp corporation", "panasonic",
                    "sony", "nintendo", "microsoft", "espressif", "tuya", "shenzhen",
                    "xiaomi", "beijing xiaomi", "ecobee", "resideo", "honeywell home",
                    "chamberlain", "lutron", "philips lighting", "signify", "wiz",
                    "tado", "netatmo", "ikea of sweden", "sengled", "leedarson",
                    "govee", "yeelight", "aqara", "lumi united", "sonoff", "itead",
                    "arlo", "skybell", "simplisafe", "abode systems", "vivint",
                    "ooma", "obihai", "vtech", "uniden", "motorola mobility",
                    "roborock", "irobot", "ecovacs", "anker", "ugreen", "baseus",
                )
            ),
        )
    }
}
