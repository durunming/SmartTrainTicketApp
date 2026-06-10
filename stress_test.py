#!/usr/bin/env python3
"""
SmartTrainTicketApp — Firebase Realtime Database 并发压力测试脚本

功能：
  1. 并发读取测试 — 多线程同时查询 trains/orders 节点，统计延迟分布
  2. 并发写入测试 — 多线程同时写入测试数据，检测 Firebase 并发处理能力
  3. 库存扣减模拟 — 模拟多用户同时购买，验证数据最终一致性
  4. 输出 P50/P90/P99 延迟报告

依赖：pip install requests
用法：python stress_test.py [--concurrency N] [--rounds R] [--mode all|read|write|stock]
"""

import json
import time
import sys
import threading
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

import requests

# ── 配置（从 google-services.json 提取） ──
FIREBASE_URL = "https://ticket-8f205-default-rtdb.firebaseio.com"
API_KEY = "AIzaSyBsMuZZfcl0z0e8WwL6GOq9TDFApNI-m6E"

# Firebase REST API 需要在 URL 后加 .json
BASE = FIREBASE_URL.rstrip("/")

# 测试用节点（不会影响真实数据）
TEST_PREFIX = "stress_test"


@dataclass
class LatencyStats:
    """延迟统计"""
    values: list = field(default_factory=list)

    def record(self, ms: float):
        self.values.append(ms)

    def report(self) -> dict:
        if not self.values:
            return {"count": 0, "min": 0, "max": 0, "avg": 0, "p50": 0, "p90": 0, "p99": 0}
        s = sorted(self.values)
        n = len(s)
        return {
            "count": n,
            "min": round(s[0], 2),
            "max": round(s[-1], 2),
            "avg": round(sum(s) / n, 2),
            "p50": round(s[int(n * 0.50)], 2),
            "p90": round(s[int(n * 0.90)], 2),
            "p99": round(s[int(n * 0.99)], 2),
        }


def http_get(path: str, params: dict = None) -> tuple[dict, float]:
    """GET 请求，返回 (响应数据, 延迟ms)"""
    url = f"{BASE}/{path}.json"
    start = time.time()
    resp = requests.get(url, params=params, timeout=10)
    elapsed = (time.time() - start) * 1000
    resp.raise_for_status()
    return resp.json(), elapsed


def http_put(path: str, data: dict) -> tuple[dict, float]:
    """PUT 请求，返回 (响应数据, 延迟ms)"""
    url = f"{BASE}/{path}.json"
    start = time.time()
    resp = requests.put(url, json=data, timeout=10)
    elapsed = (time.time() - start) * 1000
    resp.raise_for_status()
    return resp.json(), elapsed


def http_patch(path: str, data: dict) -> tuple[dict, float]:
    """PATCH 请求，返回 (响应数据, 延迟ms)"""
    url = f"{BASE}/{path}.json"
    start = time.time()
    resp = requests.patch(url, json=data, timeout=10)
    elapsed = (time.time() - start) * 1000
    resp.raise_for_status()
    return resp.json(), elapsed


def http_delete(path: str) -> tuple[dict, float]:
    """DELETE 请求"""
    url = f"{BASE}/{path}.json"
    start = time.time()
    resp = requests.delete(url, timeout=10)
    elapsed = (time.time() - start) * 1000
    resp.raise_for_status()
    return resp.json(), elapsed


# ═══════════════════════════════════════════════
#  测试 1：并发读取
# ═══════════════════════════════════════════════

def test_concurrent_reads(concurrency: int, rounds: int) -> LatencyStats:
    """多线程并发读取 trains 节点"""
    stats = LatencyStats()
    errors = 0

    def _read_worker(worker_id: int):
        nonlocal errors
        for r in range(rounds):
            try:
                _, ms = http_get("trains")
                stats.record(ms)
            except Exception as e:
                errors += 1
                print(f"  [worker {worker_id}] round {r} ERROR: {e}")

    print(f"\n{'='*60}")
    print(f"  并发读取测试：{concurrency} 线程 × {rounds} 轮 = {concurrency * rounds} 次请求")
    print(f"{'='*60}")

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(_read_worker, i) for i in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    total_time = time.time() - t0

    report = stats.report()
    print(f"\n  总耗时: {total_time:.2f}s")
    print(f"  成功: {report['count']} 次, 失败: {errors} 次")
    print(f"  QPS: {report['count'] / total_time:.1f}")
    print(f"  延迟 — min: {report['min']}ms  avg: {report['avg']}ms  max: {report['max']}ms")
    print(f"         p50: {report['p50']}ms  p90: {report['p90']}ms  p99: {report['p99']}ms")
    return stats


# ═══════════════════════════════════════════════
#  测试 2：并发写入
# ═══════════════════════════════════════════════

def test_concurrent_writes(concurrency: int, rounds: int) -> LatencyStats:
    """多线程并发写入测试数据到 stress_test 节点（不影响真实数据）"""
    stats = LatencyStats()
    errors = 0
    lock = threading.Lock()

    def _write_worker(worker_id: int):
        nonlocal errors
        for r in range(rounds):
            payload = {
                "worker": worker_id,
                "round": r,
                "timestamp": time.time(),
                "data": f"stress_test_data_{worker_id}_{r}"
            }
            try:
                path = f"{TEST_PREFIX}/writes/worker{worker_id}_round{r}"
                _, ms = http_put(path, payload)
                stats.record(ms)
            except Exception as e:
                with lock:
                    errors += 1
                    print(f"  [worker {worker_id}] round {r} ERROR: {e}")

    print(f"\n{'='*60}")
    print(f"  并发写入测试：{concurrency} 线程 × {rounds} 轮 = {concurrency * rounds} 次请求")
    print(f"{'='*60}")

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(_write_worker, i) for i in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    total_time = time.time() - t0

    report = stats.report()
    print(f"\n  总耗时: {total_time:.2f}s")
    print(f"  成功: {report['count']} 次, 失败: {errors} 次")
    print(f"  QPS: {report['count'] / total_time:.1f}")
    print(f"  延迟 — min: {report['min']}ms  avg: {report['avg']}ms  max: {report['max']}ms")
    print(f"         p50: {report['p50']}ms  p90: {report['p90']}ms  p99: {report['p99']}ms")
    return stats


# ═══════════════════════════════════════════════
#  测试 3：库存扣减模拟（并发安全验证）
# ═══════════════════════════════════════════════

def test_stock_race_condition(concurrency: int) -> dict:
    """
    模拟并发库存扣减场景：
    1. 在 stress_test 下创建一个测试商品，初始库存 = concurrency - 2（故意制造超卖条件）
    2. N 个线程同时尝试扣减 1 个库存
    3. 检查最终库存是否为负数（超卖检测）
    4. 输出并发冲突统计
    """
    initial_stock = max(1, concurrency - 2)  # 库存不足以满足所有请求
    test_path = f"{TEST_PREFIX}/stock_test"

    # 初始化库存
    print(f"\n{'='*60}")
    print(f"  库存扣减并发测试（{concurrency} 线程抢 {initial_stock} 个库存）")
    print(f"{'='*60}")
    print(f"  初始化库存: {initial_stock}")

    http_put(test_path, {"remaining": initial_stock})

    success_count = 0
    fail_count = 0
    stats = LatencyStats()
    lock = threading.Lock()

    def _buy_worker(worker_id: int):
        nonlocal success_count, fail_count
        try:
            # Step 1: 读取当前库存
            current, read_ms = http_get(test_path)
            stats.record(read_ms)

            if current is None:
                return

            remaining = current.get("remaining", 0)

            # Step 2: 如果还有库存，尝试写入（非原子的乐观锁模拟）
            if remaining > 0:
                new_val = remaining - 1
                _, write_ms = http_put(test_path, {"remaining": new_val})
                stats.record(write_ms)
                with lock:
                    success_count += 1
            else:
                with lock:
                    fail_count += 1
        except Exception as e:
            with lock:
                fail_count += 1
            print(f"  [worker {worker_id}] ERROR: {e}")

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(_buy_worker, i) for i in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    total_time = time.time() - t0

    # 读取最终库存
    final_data, _ = http_get(test_path)
    final_stock = final_data.get("remaining", 0) if final_data else 0

    # 清理测试数据
    try:
        http_delete(test_path)
    except Exception:
        pass

    report = stats.report()
    print(f"\n  总耗时: {total_time:.2f}s")
    print(f"  请求成功: {success_count} 次  失败: {fail_count} 次")
    print(f"  初始库存: {initial_stock}  →  最终库存: {final_stock}")
    if final_stock < 0:
        print(f"  [WARN]  超卖! 库存出现负数 ({final_stock}) — 需要 Firebase Transaction 保证原子性")
    else:
        print(f"  最终库存 >= 0 — 未出现超卖（但可能有少卖）")
    print(f"  延迟 — avg: {report['avg']}ms  p50: {report['p50']}ms  p99: {report['p99']}ms")

    return {
        "initial_stock": initial_stock,
        "final_stock": final_stock,
        "success": success_count,
        "fail": fail_count,
        "over_sold": final_stock < 0,
        "elapsed_s": round(total_time, 2),
    }


# ═══════════════════════════════════════════════
#  测试 4：混合负载（读写混合）
# ═══════════════════════════════════════════════

def test_mixed_load(concurrency: int, rounds: int) -> dict:
    """读写混合负载：一半线程读 trains，一半写 stress_test"""
    read_stats = LatencyStats()
    write_stats = LatencyStats()
    errors = 0
    lock = threading.Lock()

    def _mixed_worker(worker_id: int):
        nonlocal errors
        is_reader = worker_id % 2 == 0
        for r in range(rounds):
            try:
                if is_reader:
                    _, ms = http_get("trains")
                    read_stats.record(ms)
                else:
                    path = f"{TEST_PREFIX}/mixed/worker{worker_id}_r{r}"
                    _, ms = http_put(path, {"ts": time.time(), "w": worker_id})
                    write_stats.record(ms)
            except Exception as e:
                with lock:
                    errors += 1

    print(f"\n{'='*60}")
    print(f"  混合负载测试：{concurrency // 2} 读 + {concurrency - concurrency // 2} 写，各 {rounds} 轮")
    print(f"{'='*60}")

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(_mixed_worker, i) for i in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    total_time = time.time() - t0

    r = read_stats.report()
    w = write_stats.report()
    total_req = r["count"] + w["count"]
    print(f"\n  总耗时: {total_time:.2f}s")
    print(f"  总请求: {total_req}  失败: {errors}")
    print(f"  读 — avg: {r['avg']}ms  p50: {r['p50']}ms  p99: {r['p99']}ms")
    print(f"  写 — avg: {w['avg']}ms  p50: {w['p50']}ms  p99: {w['p99']}ms")
    return {"read": r, "write": w, "errors": errors}


# ═══════════════════════════════════════════════
#  清理 + 主入口
# ═══════════════════════════════════════════════

def cleanup():
    """清理所有测试数据"""
    try:
        http_delete(TEST_PREFIX)
        print(f"\n  [OK] 已清理测试数据: /{TEST_PREFIX}")
    except Exception as e:
        print(f"\n  [WARN] 清理失败: {e}")


def main():
    parser = argparse.ArgumentParser(description="Firebase Realtime Database 压力测试")
    parser.add_argument("--concurrency", "-c", type=int, default=20, help="并发线程数 (默认 20)")
    parser.add_argument("--rounds", "-r", type=int, default=3, help="每线程执行轮数 (默认 3)")
    parser.add_argument("--mode", "-m", choices=["all", "read", "write", "stock", "mixed"],
                        default="all", help="测试模式 (默认 all)")
    args = parser.parse_args()

    print("╔══════════════════════════════════════════════════════╗")
    print("║  SmartTrainTicketApp — Firebase 并发压力测试         ║")
    print("╚══════════════════════════════════════════════════════╝")
    print(f"  Firebase: {FIREBASE_URL}")
    print(f"  并发: {args.concurrency} 线程  轮次: {args.rounds}")

    results = {}

    try:
        if args.mode in ("all", "read"):
            results["read"] = test_concurrent_reads(args.concurrency, args.rounds)

        if args.mode in ("all", "write"):
            results["write"] = test_concurrent_writes(args.concurrency, args.rounds)

        if args.mode in ("all", "mixed"):
            results["mixed"] = test_mixed_load(args.concurrency, args.rounds)

        if args.mode in ("all", "stock"):
            results["stock"] = test_stock_race_condition(args.concurrency)

        # ── 最终汇总 ──
        print(f"\n{'='*60}")
        print(f"  [Summary] 测试完成汇总")
        print(f"{'='*60}")
        for name, data in results.items():
            if isinstance(data, dict):
                if "over_sold" in data:
                    status = "[OVER SOLD] 超卖" if data["over_sold"] else "[OK] 正常"
                    print(f"  [{name}] {status}  库存: {data['initial_stock']}→{data['final_stock']}")
                elif "read" in data:
                    print(f"  [{name}] 读 P50={data['read']['p50']}ms 写 P50={data['write']['p50']}ms")
            else:
                r = data.report()
                print(f"  [{name}] {r['count']}次  avg={r['avg']}ms  P99={r['p99']}ms")

        print(f"\n  提示：库存扣减测试若出现超卖，说明需要 Firebase Transaction")
        print(f"        项目中 TrainRepository.updateSeatAvailability() 已使用 runTransaction 保证原子性。")

    finally:
        if args.mode in ("all", "write", "mixed", "stock"):
            cleanup()


if __name__ == "__main__":
    main()
