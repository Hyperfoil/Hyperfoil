echo "taskset -c 2-5 jbang /home/dlovison/github/Hyperfoil/Hyperfoil/MockHttpServer.java --port 8080 --think-time 2000 --threads 4"

awk '
$8 ~ /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/ { last_date = $8 }

# 1. Match Request Start (extract timestamp AFTER "request start time:")
/Request\] request start time:/ {
    after_str = substr($0, index($0, "request start time:"))
    if (match(after_str, /[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}/)) {
        ts = substr(after_str, RSTART, RLENGTH)
        created[ts]++
        timestamps[ts]
    }
}

# 2. Match Active Statistic
/get active statistic with/ {
    # Timestamp AFTER string
    after_str = substr($0, index($0, "get active statistic with"))
    if (match(after_str, /[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}/)) {
        ts_after = substr(after_str, RSTART, RLENGTH)
        active_stats[ts_after]++
        timestamps[ts_after]
    }

    # Timestamp BEFORE string (log line prefix time: $1)
    d = ($8 ~ /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/) ? $8 : (last_date ? last_date : "NO_DATE")
    t = substr($1, 1, 8)
    ts_before = d " " t
    right_stats[ts_before]++
    timestamps[ts_before]
}

END {
    for (ts in timestamps) {
        print ts "\t" created[ts]+0 "\t" active_stats[ts]+0 "\t" right_stats[ts]+0
    }
}' /tmp/hyperfoil/hyperfoil.local.log | sort | awk -F'\t' '
BEGIN {
    printf "%-20s | %-16s | %-20s | %-20s\n", "TIMESTAMP", "REQUESTS CREATED", "ACTIVE STATS GETS", "RIGHT STATS CREATION"
    printf "--------------------------------------------------------------------------------------------------\n"
}
{
    printf "%-20s | %-16d | %-20d | %-20d\n", $1, $2, $3, $4
    tot_c += $2
    tot_a += $3
    tot_r += $4
}
END {
    printf "--------------------------------------------------------------------------------------------------\n"
    printf "%-20s | %-16d | %-20d | %-20d\n", "TOTAL", tot_c, tot_a, tot_r
}'
