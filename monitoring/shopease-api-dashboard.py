import json, urllib.request, base64

DS = {'type': 'prometheus', 'uid': 'ffsaghhzn7vuoa'}
auth = base64.b64encode(b'admin:ShopEase@Grafana2024').decode()
headers = {'Authorization': 'Basic ' + auth, 'Content-Type': 'application/json'}


def panel(title, exprs, unit, x, y, w=12, h=8, legends=None):
    targets = []
    for i, e in enumerate(exprs):
        t = {'expr': e, 'datasource': DS, 'refId': chr(65 + i)}
        if legends:
            t['legendFormat'] = legends[i]
        targets.append(t)
    return {
        'title': title,
        'type': 'timeseries',
        'datasource': DS,
        'gridPos': {'x': x, 'y': y, 'w': w, 'h': h},
        'fieldConfig': {'defaults': {'unit': unit}, 'overrides': []},
        'targets': targets,
    }


panels = [
    # Row 1 — traffic & errors
    panel('Request Rate by Endpoint (req/s)',
          ['sum by (uri) (rate(http_server_requests_seconds_count{uri!~"/actuator.*|root|UNKNOWN"}[2m]))'],
          'reqps', 0, 0, legends=['{{uri}}']),
    panel('Error Rate — 4xx / 5xx (req/s)',
          ['sum by (status) (rate(http_server_requests_seconds_count{status=~"4..|5.."}[2m]))'],
          'reqps', 12, 0, legends=['HTTP {{status}}']),

    # Row 2 — latency
    panel('Avg Latency by Endpoint (ms)',
          ['1000 * sum by (uri) (rate(http_server_requests_seconds_sum{uri!~"/actuator.*|root|UNKNOWN"}[2m])) / sum by (uri) (rate(http_server_requests_seconds_count{uri!~"/actuator.*|root|UNKNOWN"}[2m]))'],
          'ms', 0, 8, legends=['{{uri}}']),
    panel('Max Latency by Endpoint (ms)',
          ['1000 * max by (uri) (http_server_requests_seconds_max{uri!~"/actuator.*|root|UNKNOWN"})'],
          'ms', 12, 8, legends=['{{uri}}']),

    # Row 3 — DB pool & cache
    panel('HikariCP Connections',
          ['hikaricp_connections_active{application="shopease"}',
           'hikaricp_connections_idle{application="shopease"}',
           'hikaricp_connections_pending{application="shopease"}'],
          'short', 0, 16, legends=['active', 'idle', 'pending (waiting for conn!)']),
    panel('Redis Cache Hit vs Miss (ops/s)',
          ['sum by (cache) (rate(cache_gets_total{result="hit"}[2m]))',
           'sum by (cache) (rate(cache_gets_total{result="miss"}[2m]))'],
          'ops', 12, 16, legends=['{{cache}} HIT', '{{cache}} MISS']),

    # Row 4 — JVM under load
    panel('JVM Heap Used vs Max',
          ['sum(jvm_memory_used_bytes{area="heap"})', 'sum(jvm_memory_max_bytes{area="heap"})'],
          'bytes', 0, 24, legends=['used', 'max']),
    panel('GC Pause Time (ms/s) & Threads',
          ['1000 * rate(jvm_gc_pause_seconds_sum[2m])', 'jvm_threads_live_threads'],
          'short', 12, 24, legends=['GC pause ms/s ({{action}})', 'live threads']),
]

dashboard = {
    'title': 'ShopEase — API Performance',
    'uid': 'shopease-api',
    'time': {'from': 'now-30m', 'to': 'now'},
    'refresh': '10s',
    'panels': panels,
    'templating': {'list': []},
    'schemaVersion': 39,
}

payload = json.dumps({'dashboard': dashboard, 'overwrite': True, 'folderId': 0}).encode()
req = urllib.request.Request('http://localhost:3000/api/dashboards/db',
                             data=payload, headers=headers, method='POST')
print(json.loads(urllib.request.urlopen(req).read()))
