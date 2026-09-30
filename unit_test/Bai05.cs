using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai05
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai05.csv",
            "Bai05#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai05.csv", "data_csv")]
        [TestMethod]
        public void TestSolveQuadratic()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string ketqua_mongdoi = Convert.ToString(TestContext.DataRow[3]);

            float x1_mongdoi = Convert.ToSingle(
                TestContext.DataRow[4],
                System.Globalization.CultureInfo.InvariantCulture);

            float x2_mongdoi = Convert.ToSingle(
                TestContext.DataRow[5],
                System.Globalization.CultureInfo.InvariantCulture);

            bool mongdoi_nan = Convert.ToBoolean(TestContext.DataRow[6]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[7]);

            int a = int.Parse(
                Convert.ToString(
                    TestContext.DataRow[0],
                    System.Globalization.CultureInfo.InvariantCulture),
                System.Globalization.NumberStyles.Integer,
                System.Globalization.CultureInfo.InvariantCulture);

            int b = int.Parse(
                Convert.ToString(
                    TestContext.DataRow[1],
                    System.Globalization.CultureInfo.InvariantCulture),
                System.Globalization.NumberStyles.Integer,
                System.Globalization.CultureInfo.InvariantCulture);

            int c = int.Parse(
                Convert.ToString(
                    TestContext.DataRow[2],
                    System.Globalization.CultureInfo.InvariantCulture),
                System.Globalization.NumberStyles.Integer,
                System.Globalization.CultureInfo.InvariantCulture);

            float x1;
            float x2;

            string ketqua_thucte = m.SolveQuadratic(a, b, c, out x1, out x2);

            Assert.IsFalse(mongdoi_exception);
            Assert.AreEqual(ketqua_mongdoi, MaKetQua(ketqua_thucte));

            if (mongdoi_nan)
            {
                Assert.IsTrue(float.IsNaN(x1));
                Assert.IsTrue(float.IsNaN(x2));
            }
            else
            {
                Assert.AreEqual(x1_mongdoi, x1, 0.001f);
                Assert.AreEqual(x2_mongdoi, x2, 0.001f);
            }
        }

        private static string MaKetQua(string ketqua)
        {
            switch (ketqua)
            {
                case "Vô số nghiệm":
                    return "INFINITE";
                case "Vô nghiệm":
                    return "NO_SOLUTION";
                case "Có 1 nghiệm":
                    return "ONE_SOLUTION";
                case "Có nghiệm kép":
                    return "DOUBLE_ROOT";
                case "Có 2 nghiệm phân biệt":
                    return "TWO_ROOTS";
                default:
                    return ketqua;
            }
        }
    }
}