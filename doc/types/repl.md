# zprint at the REPL

[Maps](#maps)  
[Vectors](#vectors)  
[Function Definitions with specs](#function-definitions-with-specs)  
[Explore at the REPL](#explore-at-the-repl)  
[Exploring deftype](#exploring-deftype)  

## Maps
Maps are everywhere in Clojure.  But they can be hard to visualize at the
REPL.  Let's look at an example map...
### printed at the repl
```clojure
[{:detail {:ident "64:c1:2f:34", :alternate "64:c1:2f:34", :string "datacenter", :interface "3.13.168.35"}, :direction :received, :connection "2795", :reference 14873, :type "get", :time 1425704001, :code "58601"} {:detail {:ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m", :code "0xe4e9"}, :direction :transmitted, :connection "X13404", :reference 14133, :type "post", :time 1425704001, :code "0xe4e9"} {:direction :transmitted, :connection "X13404", :reference 14227, :type "post", :time 1425704001, :code "58601"} {:detail {:ident "50:56:a5:1d:61", :ip4 "3.13.171.81", :code "0x1344a676"}, :direction :received, :connection "2796", :reference 14133, :type "error", :time 1425704003, :code "0x1344a676"} {:detail {:ident "50:56:a5:1d:61", :alternate "01:50:56:a5:1d:61", :string "datacenter", :interface "3.13.168.35"}, :direction :transmitted, :connection "2796", :reference 14873, :type "error", :time 1425704003, :code "323266166"}]
```
### clojure.pprint/pprint
This is a lot better, for sure.
```clojure
[{:detail
  {:ident "64:c1:2f:34",
   :alternate "64:c1:2f:34",
   :string "datacenter",
   :interface "3.13.168.35"},
  :direction :received,
  :connection "2795",
  :reference 14873,
  :type "get",
  :time 1425704001,
  :code "58601"}
 {:detail
  {:ident "64:c1:2f:34",
   :ip4 "3.13.168.151",
   :time "30m",
   :code "0xe4e9"},
  :direction :transmitted,
  :connection "X13404",
  :reference 14133,
  :type "post",
  :time 1425704001,
  :code "0xe4e9"}
 {:direction :transmitted,
  :connection "X13404",
  :reference 14227,
  :type "post",
  :time 1425704001,
  :code "58601"}
 {:detail
  {:ident "50:56:a5:1d:61", :ip4 "3.13.171.81", :code "0x1344a676"},
  :direction :received,
  :connection "2796",
  :reference 14133,
  :type "error",
  :time 1425704003,
  :code "0x1344a676"}
 {:detail
  {:ident "50:56:a5:1d:61",
   :alternate "01:50:56:a5:1d:61",
   :string "datacenter",
   :interface "3.13.168.35"},
  :direction :transmitted,
  :connection "2796",
  :reference 14873,
  :type "error",
  :time 1425704003,
  :code "323266166"}]
```
### Classic zprint
Keys are sorted, where necessary sub-maps are paired up and hung on the 
right.
```clojure
[{:code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"},
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```
### Specify the order of the keys `{:map {:key-order [:type]}}`
```clojure
[{:type "get",
  :code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :received,
  :reference 14873,
  :time 1425704001}
 {:type "post",
  :code "0xe4e9",
  :connection "X13404",
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"},
  :direction :transmitted,
  :reference 14133,
  :time 1425704001}
 {:type "post",
  :code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001}
 {:type "error",
  :code "0x1344a676",
  :connection "2796",
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction :received,
  :reference 14133,
  :time 1425704003}
 {:type "error",
  :code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :transmitted,
  :reference 14873,
  :time 1425704003}]
```
### Justification `{:map {:key-order [:type]} :style :justified}`
If the keys are not too long, justification can help a lot.
```clojure
[{:type       "get",
  :code       "58601",
  :connection "2795",
  :detail     {:alternate "64:c1:2f:34",
               :ident     "64:c1:2f:34",
               :interface "3.13.168.35",
               :string    "datacenter"},
  :direction  :received,
  :reference  14873,
  :time       1425704001}
 {:type       "post",
  :code       "0xe4e9",
  :connection "X13404",
  :detail     {:code  "0xe4e9",
               :ident "64:c1:2f:34",
               :ip4   "3.13.168.151",
               :time  "30m"},
  :direction  :transmitted,
  :reference  14133,
  :time       1425704001}
 {:type       "post",
  :code       "58601",
  :connection "X13404",
  :direction  :transmitted,
  :reference  14227,
  :time       1425704001}
 {:type       "error",
  :code       "0x1344a676",
  :connection "2796",
  :detail     {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction  :received,
  :reference  14133,
  :time       1425704003}
 {:type       "error",
  :code       "323266166",
  :connection "2796",
  :detail     {:alternate "01:50:56:a5:1d:61",
               :ident     "50:56:a5:1d:61",
               :interface "3.13.168.35",
               :string    "datacenter"},
  :direction  :transmitted,
  :reference  14873,
  :time       1425704003}]
```
### Ignore key "detail" 
`{:map {:key-order [:type], :key-ignore-silent [:detail]}, :style :justified}`
```clojure
[{:type       "get",
  :code       "58601",
  :connection "2795",
  :direction  :received,
  :reference  14873,
  :time       1425704001}
 {:type       "post",
  :code       "0xe4e9",
  :connection "X13404",
  :direction  :transmitted,
  :reference  14133,
  :time       1425704001}
 {:type       "post",
  :code       "58601",
  :connection "X13404",
  :direction  :transmitted,
  :reference  14227,
  :time       1425704001}
 {:type       "error",
  :code       "0x1344a676",
  :connection "2796",
  :direction  :received,
  :reference  14133,
  :time       1425704003}
 {:type       "error",
  :code       "323266166",
  :connection "2796",
  :direction  :transmitted,
  :reference  14873,
  :time       1425704003}]
```
### Overview with max-length `{:map {:key-order [:type]}, :max-length [100 3 1 0]}`
`max-length` will show you a maximum number of things at each level.  In this
case, 100 things at the top level, 3 at the next level, 1 at the next level, 
and 0 at the next and all levels below.  In combination with `:key-order`, 
this can be exceptionally powerful as a way to get an overview of some
data.
```clojure
[{:type "get", :code "58601", :connection "2795", ...}
 {:type "post", :code "0xe4e9", :connection "X13404", ...}
 {:type "post", :code "58601", :connection "X13404", ...}
 {:type "error", :code "0x1344a676", :connection "2796", ...}
 {:type "error", :code "323266166", :connection "2796", ...}]
```
## Vectors
### Margins
#### pprint gets nervous about the right margin (80 columns here)
```clojure
(clojure.pprint/pprint (byte-array (range 1 400)))

[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54,
 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71,
 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88,
 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104,
 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118,
 119, 120, 121, 122, 123, 124, 125, 126, 127, -128, -127, -126, -125,
 -124, -123, -122, -121, -120, -119, -118, -117, -116, -115, -114,
 -113, -112, -111, -110, -109, -108, -107, -106, -105, -104, -103,
 -102, -101, -100, -99, -98, -97, -96, -95, -94, -93, -92, -91, -90,
 -89, -88, -87, -86, -85, -84, -83, -82, -81, -80, -79, -78, -77, -76,
 -75, -74, -73, -72, -71, -70, -69, -68, -67, -66, -65, -64, -63, -62,
 -61, -60, -59, -58, -57, -56, -55, -54, -53, -52, -51, -50, -49, -48,
 -47, -46, -45, -44, -43, -42, -41, -40, -39, -38, -37, -36, -35, -34,
 -33, -32, -31, -30, -29, -28, -27, -26, -25, -24, -23, -22, -21, -20,
 -19, -18, -17, -16, -15, -14, -13, -12, -11, -10, -9, -8, -7, -6, -5,
 -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66,
 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83,
 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114,
 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, -128,
 -127, -126, -125, -124, -123, -122, -121, -120, -119, -118, -117,
 -116, -115, -114, -113]
```
#### Classic zprint
```clojure
(zprint (byte-array (range 1 400)))

[1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29
 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55
 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81
 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99 100 101 102 103 104 105
 106 107 108 109 110 111 112 113 114 115 116 117 118 119 120 121 122 123 124 125
 126 127 -128 -127 -126 -125 -124 -123 -122 -121 -120 -119 -118 -117 -116 -115
 -114 -113 -112 -111 -110 -109 -108 -107 -106 -105 -104 -103 -102 -101 -100 -99
 -98 -97 -96 -95 -94 -93 -92 -91 -90 -89 -88 -87 -86 -85 -84 -83 -82 -81 -80 -79
 -78 -77 -76 -75 -74 -73 -72 -71 -70 -69 -68 -67 -66 -65 -64 -63 -62 -61 -60 -59
 -58 -57 -56 -55 -54 -53 -52 -51 -50 -49 -48 -47 -46 -45 -44 -43 -42 -41 -40 -39
 -38 -37 -36 -35 -34 -33 -32 -31 -30 -29 -28 -27 -26 -25 -24 -23 -22 -21 -20 -19
 -18 -17 -16 -15 -14 -13 -12 -11 -10 -9 -8 -7 -6 -5 -4 -3 -2 -1 0 1 2 3 4 5 6 7
 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34
 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60
 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86
 87 88 89 90 91 92 93 94 95 96 97 98 99 100 101 102 103 104 105 106 107 108 109
 110 111 112 113 114 115 116 117 118 119 120 121 122 123 124 125 126 127 -128
 -127 -126 -125 -124 -123 -122 -121 -120 -119 -118 -117 -116 -115 -114 -113]
```
### Looking at byte values
If you are looking at memory or network packets, you are probably
dealing with bytes and byte arrays.  Hex makes it a lot better.

#### clojure.pprint/pprint
```clojure
(clojure.pprint/write (byte-array (range 1 400)) :base 16)

[1, 2, 3, 4, 5, 6, 7, 8, 9, a, b, c, d, e, f, 10, 11, 12, 13, 14, 15,
 16, 17, 18, 19, 1a, 1b, 1c, 1d, 1e, 1f, 20, 21, 22, 23, 24, 25, 26,
 27, 28, 29, 2a, 2b, 2c, 2d, 2e, 2f, 30, 31, 32, 33, 34, 35, 36, 37,
 38, 39, 3a, 3b, 3c, 3d, 3e, 3f, 40, 41, 42, 43, 44, 45, 46, 47, 48,
 49, 4a, 4b, 4c, 4d, 4e, 4f, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
 5a, 5b, 5c, 5d, 5e, 5f, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 6a,
 6b, 6c, 6d, 6e, 6f, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 7a, 7b,
 7c, 7d, 7e, 7f, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 8a, 8b, 8c,
 8d, 8e, 8f, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 9a, 9b, 9c, 9d,
 9e, 9f, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, aa, ab, ac, ad, ae,
 af, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, ba, bb, bc, bd, be, bf,
 c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, ca, cb, cc, cd, ce, cf, d0,
 d1, d2, d3, d4, d5, d6, d7, d8, d9, da, db, dc, dd, de, df, e0, e1,
 e2, e3, e4, e5, e6, e7, e8, e9, ea, eb, ec, ed, ee, ef, f0, f1, f2,
 f3, f4, f5, f6, f7, f8, f9, fa, fb, fc, fd, fe, ff, 0, 1, 2, 3, 4, 5,
 6, 7, 8, 9, a, b, c, d, e, f, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
 1a, 1b, 1c, 1d, 1e, 1f, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 2a,
 2b, 2c, 2d, 2e, 2f, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 3a, 3b,
 3c, 3d, 3e, 3f, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 4a, 4b, 4c,
 4d, 4e, 4f, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 5a, 5b, 5c, 5d,
 5e, 5f, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 6a, 6b, 6c, 6d, 6e,
 6f, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 7a, 7b, 7c, 7d, 7e, 7f,
 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 8a, 8b, 8c, 8d, 8e, 8f]
```
#### Classic zprint `{:array {:hex? true}}`
Since every byte is the same size and there are no commas, it can be easier
to see patterns in the data.

```clojure
(zprint (byte-array (range 1 400)) {:array {:hex? true}})

[01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13 14 15 16 17 18 19 1a
 1b 1c 1d 1e 1f 20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33 34
 35 36 37 38 39 3a 3b 3c 3d 3e 3f 40 41 42 43 44 45 46 47 48 49 4a 4b 4c 4d 4e
 4f 50 51 52 53 54 55 56 57 58 59 5a 5b 5c 5d 5e 5f 60 61 62 63 64 65 66 67 68
 69 6a 6b 6c 6d 6e 6f 70 71 72 73 74 75 76 77 78 79 7a 7b 7c 7d 7e 7f 80 81 82
 83 84 85 86 87 88 89 8a 8b 8c 8d 8e 8f 90 91 92 93 94 95 96 97 98 99 9a 9b 9c
 9d 9e 9f a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 aa ab ac ad ae af b0 b1 b2 b3 b4 b5 b6
 b7 b8 b9 ba bb bc bd be bf c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 ca cb cc cd ce cf d0
 d1 d2 d3 d4 d5 d6 d7 d8 d9 da db dc dd de df e0 e1 e2 e3 e4 e5 e6 e7 e8 e9 ea
 eb ec ed ee ef f0 f1 f2 f3 f4 f5 f6 f7 f8 f9 fa fb fc fd fe ff 00 01 02 03 04
 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e
 1f 20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33 34 35 36 37 38
 39 3a 3b 3c 3d 3e 3f 40 41 42 43 44 45 46 47 48 49 4a 4b 4c 4d 4e 4f 50 51 52
 53 54 55 56 57 58 59 5a 5b 5c 5d 5e 5f 60 61 62 63 64 65 66 67 68 69 6a 6b 6c
 6d 6e 6f 70 71 72 73 74 75 76 77 78 79 7a 7b 7c 7d 7e 7f 80 81 82 83 84 85 86
 87 88 89 8a 8b 8c 8d 8e 8f] 
```
## Function Definitions with specs
If you want to see the definition of a function, just ask zprint.
Note that any specs defined for the function are included in the docstring,
nicely formatted, of course..
### Classic zprint `(czprint-fn defn)`
```clojure
(czprint-fn defn)

(def
  ^{:doc
      "Same as (def name (fn [params* ] exprs*)) or (def
    name (fn ([params* ] exprs*)+)) with any doc-string or attrs added
    to the var metadata. prepost-map defines a map with optional keys
    :pre and :post that contain collections of pre or post conditions.

  Spec:
    args: (cat :name simple-symbol?
               :docstring (? string?)
               :meta (? map?)
               :bs (alt :arity-1 :clojure.core.specs.alpha/args+body
                        :arity-n
                          (cat :bodies
                                 (+ (spec :clojure.core.specs.alpha/args+body))
                               :attr (? map?))))
    ret: any?",
    :arglists '([name doc-string? attr-map? [params*] prepost-map? body]
                [name doc-string? attr-map? ([params*] prepost-map? body) +
                 attr-map?]),
    :added "1.0"}
  defn
  (fn defn [&form &env name & fdecl]
    ;; Note: Cannot delegate this check to def because of the call to (with-meta
    ;; name ..)
    (if (instance? clojure.lang.Symbol name)
      nil
      (throw (IllegalArgumentException.
               "First argument to defn must be a symbol")))
    (let [m (if (string? (first fdecl)) {:doc (first fdecl)} {})
          fdecl (if (string? (first fdecl)) (next fdecl) fdecl)
          m (if (map? (first fdecl)) (conj m (first fdecl)) m)
          fdecl (if (map? (first fdecl)) (next fdecl) fdecl)
          fdecl (if (vector? (first fdecl)) (list fdecl) fdecl)
          m (if (map? (last fdecl)) (conj m (last fdecl)) m)
          fdecl (if (map? (last fdecl)) (butlast fdecl) fdecl)
          m (conj {:arglists (list 'quote (sigs fdecl))} m)
          m (let [inline (:inline m)
                  ifn (first inline)
                  iname (second inline)]
              ;; same as: (if (and (= 'fn ifn) (not (symbol? iname))) ...)
              (if (if (clojure.lang.Util/equiv 'fn ifn)
                    (if (instance? clojure.lang.Symbol iname) false true))
                ;; inserts the same fn name to the inline fn if it does not have
                ;; one
                (assoc m
                  :inline (cons ifn
                                (cons (clojure.lang.Symbol/intern
                                        (.concat (.getName ^clojure.lang.Symbol
                                                           name)
                                                 "__inliner"))
                                      (next inline))))
                m))
          m (conj (if (meta name) (meta name) {}) m)]
      (list 'def
            (with-meta name m)
            ;;todo - restore propagation of fn name
            ;;must figure out how to convey primitive hints to self calls first
            ;;(cons `fn fdecl)
            (with-meta (cons `fn fdecl) {:rettag (:tag m)})))))
```
### Specs for Functions
You can get specs for function from the `(doc ...)` macro too.
```clojure
zprint.core=> (doc defn)
-------------------------
clojure.core/defn
([name doc-string? attr-map? [params*] prepost-map? body] [name doc-string? attr-map? ([params*] prepost-map? body) + attr-map?])
Macro
  Same as (def name (fn [params* ] exprs*)) or (def
    name (fn ([params* ] exprs*)+)) with any doc-string or attrs added
    to the var metadata. prepost-map defines a map with optional keys
    :pre and :post that contain collections of pre or post conditions.
Spec
  args: (cat :name simple-symbol? :docstring (? string?) :meta (? map?) :bs (alt :arity-1 :clojure.core.specs.alpha/args+body :arity-n (cat :bodies (+ (spec :clojure.core.specs.alpha/args+body)) :attr (? map?))))
  ret: any?
```
Which would you prefer?

## Explore at the REPL
Want to see what `clojure.spec.alpha/describe` looks like?
### Classic zprint
```clojure
(czprint-fn clojure.spec.alpha/describe)

(defn describe
  "returns an abbreviated description of the spec as data"
  [spec]
  (abbrev (form spec)))
```
All right, what does `abbrev` do?
```clojure
(czprint-fn clojure.spec.alpha/abbrev)

(defn abbrev
  [form]
  (cond
    (seq? form)
      (walk/postwalk
        (fn [form]
          (cond (c/and (symbol? form) (namespace form)) (-> form
                                                            name
                                                            symbol)
                (c/and (seq? form) (= 'fn (first form)) (= '[%] (second form)))
                  (last form)
                :else form))
        form)
    (c/and (symbol? form) (namespace form)) (-> form
                                                name
                                                symbol)
    :else form))
```
Now, we are getting somewhere!
## Exploring deftype
Sometimes it would be nice to get an overview of a deftype without having to
wade through all of the details.
### Too much!
```clojure
(czprint-fn ->Vec)

(deftype Vec [^clojure.core.ArrayManager am ^int cnt ^int shift
              ^clojure.core.VecNode root tail _meta]
  Object
    (equals [this o]
      (cond (identical? this o) true
            (or (instance? clojure.lang.IPersistentVector o)
                (instance? java.util.RandomAccess o))
              (and (= cnt (count o))
                   (loop [i (int 0)]
                     (cond (= i cnt) true
                           (.equals (.nth this i) (nth o i)) (recur (inc i))
                           :else false)))
            (or (instance? clojure.lang.Sequential o)
                (instance? java.util.List o))
              (if-let [st (seq this)]
                (.equals st (seq o))
                (nil? (seq o)))
            :else false))
    ;todo - cache
    (hashCode [this]
;
; This goes on and on and on... 310 lines total
;
  ...))
```
### A bit more digestable
This is still a lot of information, but does give you an overview,
in 105 lines instead of 310.
```clojure
(czprint-fn ->Vec {:max-length [1000 3 10 1 0]})

(deftype Vec [^clojure.core.ArrayManager am ^int cnt ^int shift ...]
  Object
    (equals [this o]
      (cond (identical? ...) true
            (or ...) (and ...)
            (or ...) (if-let ...)
            :else false))
    ;todo - cache
    (hashCode [this] (loop [hash ...] (if ...)))
  ;todo - cache
  clojure.lang.IHashEq
    (hasheq [this] (Murmur3/hashOrdered this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta)
  clojure.lang.IObj
    (withMeta [_ m] (new Vec am cnt shift root tail m))
  clojure.lang.Indexed
    (nth [this i] (let [a ...] (.aget ...)))
    (nth [this i not-found] (let [z ...] (if ...)))
  clojure.lang.IPersistentCollection
    (cons [this val] (if (< ...) (let ...) (let ...)))
    (empty [_] (new Vec am 0 5 EMPTY-NODE (.array ...) nil))
    (equiv [this o]
      (cond (or ...) (and ...)
            (or ...) (clojure.lang.Util/equiv ...)
            :else false))
  clojure.lang.IPersistentStack
    (peek [this] (when (> ...) (.nth ...)))
    (pop [this]
      (cond (zero? ...) (throw ...)
            (= ...) (new ...)
            (> ...) (let ...)
            :else (let ...)))
  clojure.lang.IPersistentVector
    (assocN [this i val]
      (cond (and ...) (if ...)
            (= ...) (.cons ...)
            :else (throw ...)))
    (length [_] cnt)
  clojure.lang.Reversible
    (rseq [this] (if (> ...) (clojure.lang.APersistentVector$RSeq. ...) nil))
  clojure.lang.Associative
    (assoc [this k v]
      (if (clojure.lang.Util/isInteger ...) (.assocN ...) (throw ...)))
    (containsKey [this k]
      (and (clojure.lang.Util/isInteger ...) (<= ...) (< ...)))
    (entryAt [this k]
      (if (.containsKey ...) (clojure.lang.MapEntry/create ...) nil))
  clojure.lang.ILookup
    (valAt [this k not-found]
      (if (clojure.lang.Util/isInteger ...) (let ...) not-found))
    (valAt [this k] (.valAt this k nil))
  clojure.lang.IFn
    (invoke [this k]
      (if (clojure.lang.Util/isInteger ...) (let ...) (throw ...)))
  clojure.lang.Seqable
    (seq [this] (if (zero? ...) nil (VecSeq. ...)))
  clojure.lang.Sequential ;marker, no methods
  clojure.core.IVecImpl
    (tailoff [_] (- cnt (.alength ...)))
    (arrayFor [this i] (if (and ...) (if ...) (throw ...)))
    (pushTail [this level parent tailnode]
      (let [subidx (bit-and ...)
            parent ^clojure.core.VecNode ...
            ret (VecNode. ...)
            node-to-insert (if ...)]
        (aset ...)
        ret))
    (popTail [this level node] (let [node ...] (cond ...)))
    (newPath [this edit ^int ... node] (if (zero? ...) node (let ...)))
    (doAssoc [this level node i val] (let [node ...] (if ...)))
  java.lang.Comparable
    (compareTo [this o] (if (identical? ...) 0 (let ...)))
  java.lang.Iterable
    (iterator [this]
      (let [i (java.util.concurrent.atomic.AtomicInteger. ...)]
        (reify ...)))
  java.util.Collection
    (contains [this o] (boolean (some ...)))
    (containsAll [this c] (every? #(.contains ...) c))
    (isEmpty [_] (zero? cnt))
    (toArray [this] (into-array Object this))
    (toArray [this arr] (if (>= ...) (do ...) (into-array ...)))
    (size [_] cnt)
    (add [_ o] (throw (UnsupportedOperationException.)))
    (addAll [_ c] (throw (UnsupportedOperationException.)))
    (clear [_] (throw (UnsupportedOperationException.)))
    (^boolean remove [_ o] (throw (UnsupportedOperationException.)))
    (removeAll [_ c] (throw (UnsupportedOperationException.)))
    (retainAll [_ c] (throw (UnsupportedOperationException.)))
  java.util.List
    (get [this i] (.nth this i))
    (indexOf [this o] (loop [i ...] (cond ...)))
    (lastIndexOf [this o] (loop [i ...] (cond ...)))
    (listIterator [this] (.listIterator this 0))
    (listIterator [this i]
      (let [i (java.util.concurrent.atomic.AtomicInteger. ...)]
        (reify ...)))
    (subList [this a z] (subvec this a z))
    (add [_ i o] (throw (UnsupportedOperationException.)))
    (addAll [_ i c] (throw (UnsupportedOperationException.)))
    (^Object remove [_ ^int ...] (throw (UnsupportedOperationException.)))
    (set [_ i e] (throw (UnsupportedOperationException.))))
```




